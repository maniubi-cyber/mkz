"""
RAG Evaluation Pipeline — RAGAS + Langfuse

Implements evaluation of RAG system quality using:
- **Faithfulness**: measures whether the generated answer is grounded in the retrieved context
- **Answer Relevancy**: measures how relevant the answer is to the question
- **Context Precision**: measures the precision of retrieved context (useful vs irrelevant)

Evaluation is tracked and visualized in Langfuse for trend monitoring.

Usage::

    from app.services.evaluator import RAGEvaluator

    evaluator = RAGEvaluator()
    result = evaluator.evaluate(
        question="公司的战略目标是什么？",
        context=["公司2024年战略目标是..."],
        answer="公司的战略目标是实现数字化转型...",
    )
    print(result)
"""

from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass, field
from typing import Optional

from app.core.config import settings

logger = logging.getLogger(__name__)

# ------------------------------------------------------------------
# RAGAS 导入
#
# ragas 的 API 在 0.1 → 0.2 之间有破坏性变更，这里做版本自适应：
#   - 0.2+ : EvaluationDataset + 类式 metric（Faithfulness()）+ LangchainLLMWrapper
#   - 0.1.x: datasets.Dataset + 小写 metric 单例（faithfulness）
#
# 注意：绝对不要在这里导入用不到的符号（例如已被移除的 ragas.chains），
# 否则一个 ImportError 会让整个评测模块静默降级为「不可用」，
# 而调用方只会看到分数恒为 0，很难察觉。
# ------------------------------------------------------------------
RAGAS_AVAILABLE = False
RAGAS_API = None  # "v2" | "v1"

try:
    from ragas import evaluate as ragas_evaluate

    try:
        # ragas >= 0.2 的推荐用法
        from ragas import EvaluationDataset
        from ragas.llms import LangchainLLMWrapper
        from ragas.metrics import (
            Faithfulness,
            ResponseRelevancy,
            LLMContextPrecisionWithoutReference,
        )

        RAGAS_API = "v2"
    except ImportError:
        # 回退到 0.1.x 的小写单例写法
        from ragas.metrics import (  # type: ignore[no-redef]
            faithfulness,
            answer_relevancy,
            context_precision,
        )

        RAGAS_API = "v1"

    RAGAS_AVAILABLE = True
    logger.info("RAGAS loaded (api=%s)", RAGAS_API)
except ImportError as exc:
    logger.warning(
        "ragas not available (%s). Install with: pip install 'ragas>=0.2.0'", exc
    )

# ------------------------------------------------------------------
# Langfuse 导入（v2 与 v3 的 trace API 完全不同，运行时再分支）
# ------------------------------------------------------------------
try:
    from langfuse import Langfuse

    LANGFUSE_AVAILABLE = True
except ImportError:
    LANGFUSE_AVAILABLE = False
    logger.warning("langfuse not installed. Install with: pip install langfuse")


# ============================================================
# Evaluation Result
# ============================================================

@dataclass
class EvaluationResult:
    """Result of a RAG evaluation run."""

    question: str
    contexts: list[str]
    answer: str
    faithfulness: float = 0.0
    answer_relevancy: float = 0.0
    context_precision: float = 0.0
    overall_score: float = 0.0
    details: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "question": self.question,
            "contexts": self.contexts,
            "answer": self.answer,
            "faithfulness": round(self.faithfulness, 4),
            "answer_relevancy": round(self.answer_relevancy, 4),
            "context_precision": round(self.context_precision, 4),
            "overall_score": round(self.overall_score, 4),
            "details": self.details,
        }


# ============================================================
# RAG Evaluator
# ============================================================

class RAGEvaluator:
    """
    RAG evaluation pipeline using RAGAS metrics and Langfuse tracking.

    Metrics:
    - faithfulness (0~1): whether the answer is grounded in the context
    - answer_relevancy (0~1): how relevant the answer is to the question
    - context_precision (0~1): precision of retrieved context
    """

    def __init__(self) -> None:
        self._llm_client = None
        self._embeddings = None
        self._langfuse: Optional["Langfuse"] = None

    def _get_llm(self):
        """
        Build the evaluator LLM.

        ragas >= 0.2 requires the LLM to be wrapped in ``LangchainLLMWrapper``;
        0.1.x accepts a raw LangChain chat model.
        """
        from langchain_openai import ChatOpenAI

        if self._llm_client is None:
            base_llm = ChatOpenAI(
                model=settings.LLM_MODEL_NAME,
                openai_api_key=settings.LLM_API_KEY,
                openai_api_base=settings.LLM_BASE_URL,
                temperature=0,
            )
            if RAGAS_API == "v2":
                self._llm_client = LangchainLLMWrapper(base_llm)
            else:
                self._llm_client = base_llm
        return self._llm_client

    def _get_embeddings(self):
        """
        Build the evaluator embeddings.

        ``answer_relevancy`` / ``ResponseRelevancy`` needs embeddings to compare the
        generated answer against questions reverse-engineered from it. We reuse the
        project's already-loaded sentence-transformers model instead of pulling a
        second one (and instead of silently defaulting to OpenAI embeddings, which
        would fail against a DeepSeek-compatible endpoint).
        """
        if self._embeddings is not None:
            return self._embeddings

        try:
            from langchain_core.embeddings import Embeddings

            from app.services.embedder import get_embedder

            class _ProjectEmbeddings(Embeddings):
                """Adapter exposing the project embedder via the LangChain interface."""

                def embed_documents(self, texts: list[str]) -> list[list[float]]:
                    return get_embedder().embed(texts).tolist()

                def embed_query(self, text: str) -> list[float]:
                    return get_embedder().embed_query(text).tolist()

            adapter = _ProjectEmbeddings()

            if RAGAS_API == "v2":
                from ragas.embeddings import LangchainEmbeddingsWrapper

                self._embeddings = LangchainEmbeddingsWrapper(adapter)
            else:
                self._embeddings = adapter
        except Exception as exc:  # pragma: no cover - depends on optional deps
            logger.warning(
                "Failed to build evaluator embeddings (%s); "
                "answer_relevancy may be skipped by ragas",
                exc,
            )
            self._embeddings = None

        return self._embeddings

    def _build_metrics(self) -> list:
        """Instantiate the three RAG metrics for the detected ragas API."""
        if RAGAS_API == "v2":
            return [
                Faithfulness(),
                ResponseRelevancy(),
                LLMContextPrecisionWithoutReference(),
            ]
        return [faithfulness, answer_relevancy, context_precision]

    def _build_dataset(self, question: str, contexts: list[str], answer: str):
        """Build the evaluation dataset in the shape the installed ragas expects."""
        if RAGAS_API == "v2":
            return EvaluationDataset.from_list(
                [
                    {
                        "user_input": question,
                        "retrieved_contexts": contexts,
                        "response": answer,
                    }
                ]
            )

        from datasets import Dataset

        # NOTE: contexts must be a list-of-lists (one list per sample),
        # otherwise ragas silently produces NaN scores.
        return Dataset.from_dict(
            {
                "question": [question],
                "contexts": [contexts],
                "answer": [answer],
            }
        )

    @staticmethod
    def _extract_score(evaluation, *names: str) -> float:
        """
        Pull a single metric score out of a ragas result.

        The return shape differs across versions: it may be a scalar, a list of
        per-sample scores, or a nested dict. Metric keys were also renamed
        (``answer_relevancy`` → ``semantic_similarity``/``answer_relevancy``,
        ``context_precision`` → ``llm_context_precision_without_reference``),
        so several candidate names are tried before giving up.
        """
        for name in names:
            try:
                value = evaluation[name]
            except (KeyError, TypeError):
                continue

            # per-sample list / numpy array
            if isinstance(value, (list, tuple)):
                numeric = [v for v in value if isinstance(v, (int, float))]
                if numeric:
                    return float(sum(numeric) / len(numeric))
                continue

            if hasattr(value, "mean"):  # numpy array / pandas Series
                try:
                    return float(value.mean())
                except Exception:
                    pass

            try:
                return float(value)
            except (TypeError, ValueError):
                continue

        logger.warning("Metric not found in ragas result, tried: %s", names)
        return 0.0

    def _get_langfuse(self) -> Optional["Langfuse"]:
        """Get Langfuse client if configured."""
        if not LANGFUSE_AVAILABLE:
            return None

        if self._langfuse is None:
            langfuse_public_key = os.getenv("LANGFUSE_PUBLIC_KEY")
            langfuse_secret_key = os.getenv("LANGFUSE_SECRET_KEY")
            langfuse_host = os.getenv("LANGFUSE_HOST", "https://cloud.langfuse.com")

            if langfuse_public_key and langfuse_secret_key:
                self._langfuse = Langfuse(
                    public_key=langfuse_public_key,
                    secret_key=langfuse_secret_key,
                    host=langfuse_host,
                )
                logger.info("Langfuse client initialized: host=%s", langfuse_host)
            else:
                logger.info("Langfuse not configured (missing LANGFUSE_PUBLIC_KEY/LANGFUSE_SECRET_KEY)")

        return self._langfuse

    def evaluate_single(
        self,
        question: str,
        contexts: list[str],
        answer: str,
    ) -> EvaluationResult:
        """
        Evaluate a single RAG interaction.

        Args:
            question:  The user's question.
            contexts:  List of retrieved context chunks.
            answer:    The LLM-generated answer.

        Returns:
            EvaluationResult with metric scores.
        """
        result = EvaluationResult(
            question=question,
            contexts=contexts,
            answer=answer,
        )

        if not RAGAS_AVAILABLE:
            logger.warning("RAGAS not available, skipping evaluation")
            result.details["skipped"] = "ragas_not_installed"
            return result

        if not contexts:
            # 没有召回内容时 faithfulness / context_precision 无意义，
            # 直接跳过，避免把 0 分误当作「模型幻觉严重」。
            logger.warning("Empty contexts, skipping evaluation")
            result.details["skipped"] = "empty_contexts"
            return result

        try:
            dataset = self._build_dataset(question, contexts, answer)

            logger.info("Running RAGAS evaluation (api=%s)...", RAGAS_API)
            eval_kwargs = {
                "metrics": self._build_metrics(),
                "llm": self._get_llm(),
            }
            embeddings = self._get_embeddings()
            if embeddings is not None:
                eval_kwargs["embeddings"] = embeddings

            evaluation = ragas_evaluate(dataset, **eval_kwargs)

            # 指标键名在不同版本间被重命名过，逐个候选尝试
            result.faithfulness = self._extract_score(evaluation, "faithfulness")
            result.answer_relevancy = self._extract_score(
                evaluation, "answer_relevancy", "response_relevancy"
            )
            result.context_precision = self._extract_score(
                evaluation,
                "context_precision",
                "llm_context_precision_without_reference",
            )
            result.overall_score = (
                result.faithfulness * 0.4
                + result.answer_relevancy * 0.3
                + result.context_precision * 0.3
            )

            logger.info(
                "RAGAS evaluation complete: "
                "faithfulness=%.4f, answer_relevancy=%.4f, "
                "context_precision=%.4f, overall=%.4f",
                result.faithfulness,
                result.answer_relevancy,
                result.context_precision,
                result.overall_score,
            )

            # Track in Langfuse
            self._track_in_langfuse(result)

        except Exception as e:
            logger.error("RAGAS evaluation failed: %s", e, exc_info=True)
            result.details["error"] = str(e)

        return result

    def evaluate_batch(
        self,
        evaluations: list[tuple[str, list[str], str]],
    ) -> list[EvaluationResult]:
        """
        Evaluate multiple RAG interactions in batch.

        Args:
            evaluations: List of (question, contexts, answer) tuples.

        Returns:
            List of EvaluationResult objects.
        """
        results = []
        for i, (question, contexts, answer) in enumerate(evaluations):
            logger.info("Evaluating sample %d/%d", i + 1, len(evaluations))
            result = self.evaluate_single(question, contexts, answer)
            results.append(result)
        return results

    def _track_in_langfuse(self, result: EvaluationResult) -> None:
        """
        Track the evaluation result in Langfuse.

        The Langfuse Python SDK changed shape between major versions:
          - v2: ``langfuse.trace(...)`` returns a StatefulTraceClient (NOT a context
            manager — using ``with`` raises ``AttributeError: __enter__``).
          - v3: traces are OTEL spans, created via ``start_as_current_span(...)``,
            and scores are attached with ``create_score``/``score``.
          - v4: ``trace()`` 与 ``start_as_current_span()`` 均已移除；改为
            ``start_as_current_observation(...)``（OTEL 上下文管理器）+ ``score_current_trace``。
            实测 langfuse 4.14.2 实例只有 observation 系列 API（2026-08-01）。

        按实例能力探测分支（v4 优先），任何一条路径失败都不会中断评测本身。
        """
        langfuse = self._get_langfuse()
        if not langfuse:
            return

        scores = {
            "faithfulness": result.faithfulness,
            "answer_relevancy": result.answer_relevancy,
            "context_precision": result.context_precision,
            "overall_score": result.overall_score,
        }

        try:
            if hasattr(langfuse, "start_as_current_observation"):
                self._track_v4(langfuse, result, scores)
            elif hasattr(langfuse, "start_as_current_span"):
                self._track_v3(langfuse, result, scores)
            else:
                self._track_v2(langfuse, result, scores)

            # 尽量刷盘：评测通常在短生命周期的请求里跑完，
            # 不 flush 可能导致进程退出前数据还在缓冲区里。
            if hasattr(langfuse, "flush"):
                langfuse.flush()

            logger.info(
                "Tracked evaluation in Langfuse: overall=%.4f", result.overall_score
            )
        except Exception as e:
            logger.warning("Failed to track in Langfuse: %s", e)

    @staticmethod
    def _track_v4(langfuse, result: EvaluationResult, scores: dict) -> None:
        """Langfuse v4 (OTEL-based) tracking.

        v4 移除了 v2 的 ``trace()`` 与 v3 的 ``start_as_current_span()``，
        改为 ``create_trace_id()`` + ``start_as_current_observation(...)``
        （上下文管理器）+ ``score_current_trace(...)``。参数均为 keyword-only。
        """
        trace_id = langfuse.create_trace_id()
        with langfuse.start_as_current_observation(
            name="rag-evaluation",
            as_type="span",
            input={"question": result.question},
            output=result.to_dict(),
            metadata=scores,
        ):
            for name, value in scores.items():
                langfuse.score_current_trace(name=name, value=float(value))
        logger.info("Tracked evaluation in Langfuse v4: trace_id=%s", trace_id)

    @staticmethod
    def _track_v3(langfuse, result: EvaluationResult, scores: dict) -> None:
        """Langfuse v3 (OTEL-based) tracking."""
        with langfuse.start_as_current_span(name="rag-evaluation") as span:
            span.update_trace(
                input={"question": result.question},
                output=result.to_dict(),
                user_id="system",
                metadata=scores,
            )
            for name, value in scores.items():
                langfuse.create_score(name=name, value=float(value))

    @staticmethod
    def _track_v2(langfuse, result: EvaluationResult, scores: dict) -> None:
        """Langfuse v2 (StatefulClient) tracking."""
        trace = langfuse.trace(
            name="rag-evaluation",
            input={"question": result.question},
            output=result.to_dict(),
            user_id="system",
            metadata=scores,
        )
        trace.generation(
            name="rag-answer",
            model=settings.LLM_MODEL_NAME,
            input=[{"role": "user", "content": result.question}],
            output=result.answer,
            metadata=scores,
        )
        for name, value in scores.items():
            trace.score(name=name, value=float(value))

    def generate_report(self, results: list[EvaluationResult]) -> dict:
        """
        Generate an evaluation report from multiple results.

        Args:
            results: List of EvaluationResult objects.

        Returns:
            Summary report with averages and trends.
        """
        if not results:
            return {"message": "No evaluation results"}

        avg_faithfulness = sum(r.faithfulness for r in results) / len(results)
        avg_relevancy = sum(r.answer_relevancy for r in results) / len(results)
        avg_precision = sum(r.context_precision for r in results) / len(results)
        avg_overall = sum(r.overall_score for r in results) / len(results)

        report = {
            "total_evaluations": len(results),
            "average_scores": {
                "faithfulness": round(avg_faithfulness, 4),
                "answer_relevancy": round(avg_relevancy, 4),
                "context_precision": round(avg_precision, 4),
                "overall_score": round(avg_overall, 4),
            },
            "trend": "stable",
            "recommendations": [],
        }

        # Generate recommendations based on scores
        if avg_faithfulness < 0.7:
            report["recommendations"].append(
                "Faithfulness below 0.7: consider reducing context size or "
                "improving prompt constraints to prevent hallucination."
            )
        if avg_relevancy < 0.7:
            report["recommendations"].append(
                "Answer relevancy below 0.7: consider adjusting retrieval "
                "parameters (top_k, alpha) or improving query rewriting."
            )
        if avg_precision < 0.7:
            report["recommendations"].append(
                "Context precision below 0.7: consider increasing similarity "
                "threshold or improving hybrid search weights."
            )

        return report


# ============================================================
# Module-level singleton
# ============================================================

_evaluator: Optional[RAGEvaluator] = None


def get_evaluator() -> RAGEvaluator:
    """Return the cached RAGEvaluator singleton."""
    global _evaluator
    if _evaluator is None:
        _evaluator = RAGEvaluator()
    return _evaluator


def evaluate_rag(
    question: str,
    contexts: list[str],
    answer: str,
) -> EvaluationResult:
    """Convenience: evaluate a single RAG interaction."""
    return get_evaluator().evaluate_single(question, contexts, answer)
