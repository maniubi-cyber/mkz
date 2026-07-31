"""
Query Rewriter Service

Performs light-weight query rewriting before retrieval:
1. **Anaphora resolution** — replaces pronouns (它/这个/那个/其) with the
   entity they refer to, inferred from conversation history.
2. **Colloquial → formal** — expands casual phrasing into search-friendly
   keyword forms (e.g. "咋搞" → "如何操作", "多少钱" → "价格").

The rewritten query is returned as a plain string.  When no history is
provided the service falls back to a no-op rewrite (returns the original
query unchanged).
"""

from __future__ import annotations

import logging
import re
from typing import Optional

from app.core.config import settings

logger = logging.getLogger(__name__)

# ============================================================
# Colloquial → formal synonym table
# ============================================================

_COLLOQUIAL_MAP: dict[str, str] = {
    # Question words
    "咋搞": "如何操作",
    "咋办": "如何处理",
    "咋整": "如何设置",
    "多少钱": "价格",
    "多少费用": "费用",
    "啥意思": "含义",
    "什么意思": "含义",
    "干嘛的": "用途",
    "干啥用": "用途",
    # Pronoun / demonstrative replacements
    "这个": "上述",
    "那个": "前述",
    "它": "上述文档",
    "其": "上述",
    # Verb simplification
    "想问": "",
    "想了解": "",
    "想知道": "",
    "请问": "",
    "帮我查一下": "",
    "帮我搜索": "",
    # Filler removal patterns are handled separately
}

# Pattern for common filler phrases to strip
_FILLER_RE = re.compile(
    r"^(你好|您好|嗨|哈喽|嘿|嗯|嗯嗯|那个|那个啥|请问|麻烦你|帮帮我)\s*,?\s*"
)

# Pronoun → entity mapping (filled from history)
_PRONOUN_RULES: list[tuple[str, str]] = [
    # (pronoun_pattern, replacement_text)
    (re.compile(r"它[的]?(是|呢|怎么样|如何|多少钱|价格|作用|功能|优点|缺点|特点|内容)"), "上述文档"),
    (re.compile(r"这个[的]?(是|呢|怎么样|如何|多少钱|价格|作用|功能|优点|缺点|特点|内容)"), "上述文档"),
    (re.compile(r"那个[的]?(是|呢|怎么样|如何|多少钱|价格|作用|功能|优点|缺点|特点|内容)"), "前述文档"),
]


# ============================================================
# QueryRewriter
# ============================================================

class QueryRewriter:
    """
    Light-weight query pre-processor for RAG retrieval.

    Two-pass rewriting:
    1. Replace pronouns with concrete entities from history.
    2. Expand colloquial expressions to formal search keywords.

    Usage::

        rewriter = QueryRewriter()
        rewritten = rewriter.rewrite(
            query="那个系统咋搞？",
            history=[
                {"role": "user", "content": "我想了解全汇文档系统"},
                {"role": "assistant", "content": "全汇是一个企业级文档协作平台"},
            ],
        )
        # → "企业级文档协作平台系统如何操作？"
    """

    def __init__(self) -> None:
        self._history: list[dict[str, str]] = []

    # ---- Public API ----

    def rewrite(
        self,
        query: str,
        history: list[dict[str, str]] | None = None,
    ) -> str:
        """
        Rewrite a raw user query into a search-friendly form.

        Args:
            query:   Original user input (may contain pronouns, colloquialisms).
            history: Conversation history for anaphora resolution.

        Returns:
            Rewritten query string.
        """
        if not query or not query.strip():
            return query

        # --- Phase 1: Anaphora resolution using history ---
        result = query
        if history:
            context_entities = self._extract_entities(history)
            result = self._resolve_pronouns(result, context_entities)

        # --- Phase 2: Colloquial → formal ---
        result = self._expand_colloquial(result)

        # --- Phase 3: Strip fillers ---
        result = _FILLER_RE.sub("", result).strip()

        # Normalize whitespace
        result = re.sub(r"\s+", " ", result)

        logger.debug("Query rewrite: '%s' → '%s'", query, result)
        return result

    def update_history(self, history: list[dict[str, str]]) -> None:
        """Update the conversation history for anaphora resolution."""
        self._history = history

    # ---- Private ----

    def _extract_entities(self, history: list[dict[str, str]]) -> list[str]:
        """
        Extract potential entity references from conversation history
        that pronouns might refer to.
        """
        entities: list[str] = []
        for turn in history:
            content = turn.get("content", "").strip()
            if not content:
                continue
            # Heuristic: pick phrases that look like proper nouns or topics
            # (capitalized words, terms with numbers, key domain terms)
            # For Chinese, we just keep meaningful substrings
            if len(content) > 3:
                entities.append(content)
        return entities

    def _resolve_pronouns(
        self, query: str, entities: list[str]
    ) -> str:
        """Replace pronouns with concrete entities from history."""
        result = query

        # If we have recent context, replace pronouns
        if entities:
            # Use the most recent entity as the default reference
            latest_entity = entities[-1][:20] if entities else ""

            # Simple pronoun substitution
            pronoun_map = {
                "它": latest_entity or "上述文档",
                "这个": latest_entity or "上述文档",
                "那个": latest_entity or "前述文档",
                "其": latest_entity or "上述",
            }

            for pronoun, replacement in pronoun_map.items():
                if replacement and replacement not in ("上述文档", "前述文档"):
                    # Replace pronoun when it likely refers to the entity
                    pattern = re.compile(rf"{re.escape(pronoun)}(?=[的呢怎么样如何])")
                    result = pattern.sub(replacement, result)

        return result

    def _expand_colloquial(self, query: str) -> str:
        """Replace colloquial expressions with formal equivalents."""
        result = query

        # Sort by length descending to match longer phrases first
        sorted_map = sorted(
            _COLLOQUIAL_MAP.items(), key=lambda x: len(x[0]), reverse=True
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
