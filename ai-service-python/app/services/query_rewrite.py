"""
Query Rewriter Service — LLM 轻量改写

用户 query 进入检索前经过 LLM 轻量改写:
1. **指代消解** — 解析「它/这个/那个」等代词，替换为历史中的具体实体
2. **口语化表述修正** — 将「咋搞/多少钱/啥意思」等口语改为检索友好的正式表述

策略:
- 优先调用 LLM 进行改写（理解上下文 + 语义级修正）
- LLM 不可用或失败时，回退到规则匹配（_COLLOQUIAL_MAP + 代词替换）
- 无 history 时跳过指代消解，仅做口语化修正

输出: 改写后的 query 字符串（纯文本，供 Embedding + BM25 检索）
"""

from __future__ import annotations

import logging
import re
from typing import Optional

from app.core.config import settings

logger = logging.getLogger(__name__)

# ============================================================
# 规则兜底表（LLM 失败时使用）
# ============================================================

_COLLOQUIAL_MAP: dict[str, str] = {
    "咋搞": "如何操作",
    "咋办": "如何处理",
    "咋整": "如何设置",
    "多少钱": "价格",
    "多少费用": "费用",
    "啥意思": "含义",
    "什么意思": "含义",
    "干嘛的": "用途",
    "干啥用": "用途",
    "这个": "上述",
    "那个": "前述",
    "它": "上述文档",
    "其": "上述",
    "想问": "",
    "想了解": "",
    "想知道": "",
    "请问": "",
    "帮我查一下": "",
    "帮我搜索": "",
}

_FILLER_RE = re.compile(
    r"^(你好|您好|嗨|哈喽|嘿|嗯|嗯嗯|那个|那个啥|请问|麻烦你|帮帮我)\s*,?\s*"
)


# ============================================================
# LLM 改写 Prompt
# ============================================================

_REWRITE_SYSTEM_PROMPT = (
    "你是一个查询改写助手。请将用户的提问改写为更适合知识库检索的表述，"
    "只做两件事: "
    "1) 指代消解: 将代词（它/这个/那个/其）替换为历史对话中的具体实体; "
    "2) 口语化修正: 将口语表述改为正式检索用语（如「咋搞」→「如何操作」）。"
    "保持原意，不要回答问题本身，不要扩展内容，不要添加解释。"
    "只输出改写后的句子，不要加引号或前缀。"
)


# ============================================================
# QueryRewriter
# ============================================================

class QueryRewriter:
    """
    LLM 轻量查询改写器（规则兜底）。

    Usage::

        rewriter = QueryRewriter()
        rewritten = rewriter.rewrite(
            query="那个系统咋搞？",
            history=[
                {"role": "user", "content": "我想了解共汇文档系统"},
                {"role": "assistant", "content": "共汇是一个企业级文档协作平台"},
            ],
        )
        # → "共汇文档系统如何操作？"
    """

    def __init__(self) -> None:
        self._llm = None

    def _get_llm(self):
        if self._llm is None:
            from app.services.llm_client import get_llm_client
            self._llm = get_llm_client()
        return self._llm

    # ---- Public API ----

    def rewrite(
        self,
        query: str,
        history: list[dict[str, str]] | None = None,
    ) -> str:
        """
        改写用户查询。

        Args:
            query:   原始用户输入（可能含代词、口语）
            history: 对话历史（用于指代消解）

        Returns:
            改写后的查询字符串
        """
        if not query or not query.strip():
            return query

        # 无 history 且无明显口语词时，跳过 LLM 调用，直接走规则快速路径
        if not history and not self._has_colloquial(query):
            return self._rule_rewrite(query, history)

        # 优先 LLM 改写
        try:
            rewritten = self._llm_rewrite(query, history)
            if rewritten and rewritten.strip():
                logger.info(
                    "LLM query rewrite: '%s' → '%s'", query, rewritten,
                )
                return rewritten.strip()
        except Exception as e:
            logger.warning("LLM 改写失败，回退规则匹配: %s", e)

        # 兜底规则
        return self._rule_rewrite(query, history)

    # ---- LLM 改写 ----

    def _llm_rewrite(
        self,
        query: str,
        history: list[dict[str, str]] | None,
    ) -> str:
        """调用 LLM 进行查询改写。"""
        llm = self._get_llm()

        # 拼接历史上下文（最多取最近 4 轮）
        context = ""
        if history:
            recent = history[-4:]
            context = "\n".join(
                f"{'用户' if h.get('role') == 'user' else '助手'}: {h.get('content', '')}"
                for h in recent
            )

        user_msg = query
        if context:
            user_msg = f"对话历史:\n{context}\n\n当前提问: {query}\n\n请改写当前提问:"

        messages = [
            {"role": "system", "content": _REWRITE_SYSTEM_PROMPT},
            {"role": "user", "content": user_msg},
        ]

        answer, _ = llm.chat(messages, temperature=0.0)

        # 清理 LLM 输出（去引号、去前缀）
        cleaned = answer.strip().strip('"').strip("'").strip("「").strip("」")
        cleaned = re.sub(r"^(改写后|改写|答案)[:：]\s*", "", cleaned)

        return cleaned

    # ---- 规则兜底 ----

    def _rule_rewrite(
        self,
        query: str,
        history: list[dict[str, str]] | None,
    ) -> str:
        """规则匹配改写（LLM 不可用时的 fallback）。"""
        result = query

        if history:
            entities = self._extract_entities(history)
            result = self._resolve_pronouns(result, entities)

        result = self._expand_colloquial(result)
        result = _FILLER_RE.sub("", result).strip()
        result = re.sub(r"\s+", " ", result)

        return result

    def _has_colloquial(self, query: str) -> bool:
        """检测是否含口语词（决定是否走 LLM）。"""
        for word in _COLLOQUIAL_MAP:
            if word in query:
                return True
        return False

    def _extract_entities(self, history: list[dict[str, str]]) -> list[str]:
        """从历史中抽取候选实体（用于代词替换）。"""
        entities: list[str] = []
        for turn in history:
            content = turn.get("content", "").strip()
            if len(content) > 3:
                entities.append(content)
        return entities

    def _resolve_pronouns(
        self, query: str, entities: list[str],
    ) -> str:
        """代词替换（规则兜底）。"""
        if not entities:
            return query

        latest_entity = entities[-1][:20]
        pronoun_map = {
            "它": latest_entity or "上述文档",
            "这个": latest_entity or "上述文档",
            "那个": latest_entity or "前述文档",
        }

        result = query
        for pronoun, replacement in pronoun_map.items():
            if replacement and replacement not in ("上述文档", "前述文档"):
                pattern = re.compile(rf"{re.escape(pronoun)}(?=[的呢怎么样如何])")
                result = pattern.sub(replacement, result)

        return result

    def _expand_colloquial(self, query: str) -> str:
        """口语化表述修正（规则兜底）。"""
        result = query
        sorted_map = sorted(
            _COLLOQUIAL_MAP.items(), key=lambda x: len(x[0]), reverse=True,
        )
        for colloquial, formal in sorted_map:
            if colloquial in result:
                result = result.replace(colloquial, formal)
        return result


# ============================================================
# Module-level singleton
# ============================================================

_rewriter: Optional[QueryRewriter] = None


def get_rewriter() -> QueryRewriter:
    """Return the cached QueryRewriter singleton."""
    global _rewriter
    if _rewriter is None:
        _rewriter = QueryRewriter()
    return _rewriter


def rewrite_query(
    query: str,
    history: list[dict[str, str]] | None = None,
) -> str:
    """Convenience: rewrite a query string."""
    return get_rewriter().rewrite(query, history)
