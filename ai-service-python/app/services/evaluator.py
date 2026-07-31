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

try:
    from ragas import evaluate as ragas_evaluate
    from ragas.metrics import faithfulness, answer_relevancy, context_precision
    from ragas.llms import LangchainLLM
    from ragas.chains import FaithfulnessQuestionGenerator

    RAGAS_AVAILABLE = True
except ImportError:
    RAGAS_AVAILABLE = False
    logger.warning("ragas not installed. Install with: pip install ragas")

try:
    from langfuse import Langfuse
    from langfuse.decorators import observe

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
        self._langfuse: Optional["Langfuse"] = None

    def _get_llm(self):
        """Get the LLM for RAGAS evaluation."""
        from langchain_openai import ChatOpenAI

        if self._llm_client is None:
            self._llm_client = ChatOpenAI(
                model=settings.LLM_MODEL_NAME,
                openai_api_key=settings.LLM_API_KEY,
                openai_api_base=settings.LLM_BASE_URL,
                temperature=0,
            )
        return self._llm_client

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
            return result

        try:
            # Build dataset for RAGAS
            from datasets import Dataset

            data = {
                "question": [question],
                "contexts": [contexts],
                "answer": [answer],
            }
            dataset = Dataset.from_dict(data)

            # Run evaluation
            logger.info("Running RAGAS evaluation...")
            evaluation = ragas_evaluate(
                dataset,
                metrics=[faithfulness, answer_relevancy, context_precision],
                llm=self._get_llm(),
            )

            # Extract scores
            result.faithfulness = float(evaluation["faithfulness"])
            result.answer_relevancy = float(evaluation["answer_relevancy"])
            result.context_precision = float(evaluation["context_precision"])
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
        """Track evaluation result in Langfuse for visualization."""
        langfuse = self._get_langfuse()
        if not langfuse:
            return

        try:
            with langfuse.trace(
                name="rag-evaluation",
                trace_id=f"eval-{result.question[:20]}-{id(result)}",
                user_id="system",
            ) as trace:
                trace.update(
                    output=result.to_dict(),
                    metadata={
                        "faithfulness": result.faithfulness,
                        "answer_relevancy": result.answer_relevancy,
                        "context_precision": result.context_precision,
                        "overall_score": result.overall_score,
                    },
                )

                # Add generation for the answer
                trace.generation(
                    name="rag-answer",
                    model=settings.LLM_MODEL_NAME,
                    input=[{"role": "user", "content": result.question}],
                    output=result.answer,
                    metadata={
                        "faithfulness": result.faithfulness,
                        "answer_relevancy": result.answer_relevancy,
                        "context_precision": result.context_precision,
                    },
                )

            logger.info("Tracked evaluation in Langfuse: overall=%.4f", result.overall_score)
        except Exception as e:
            logger.warning("Failed to track in Langfuse: %s", e)

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
