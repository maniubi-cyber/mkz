"""
LLM Metadata Extractor

调用 LLM 自动为每个父切块提取元数据（主题 / 关键词），
随子块 embedding 一起写入 Qdrant payload，用于：
- 元数据权限过滤（visibility / org_id 已独立维护，此处补充语义元数据）
- 检索结果二次筛选（按主题、关键词过滤）
- 提升召回相关性（payload 可叠加关键词命中加权）

设计要点:
- 批量喂给 LLM（LLM_METADATA_BATCH_SIZE），降低 token 消耗
- 输出强制 JSON，避免解析失败
- 失败时返回空元数据，不阻塞主流程
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any, Optional

from app.core.config import settings

logger = logging.getLogger(__name__)


_META_SYSTEM_PROMPT = (
    "你是一个文档元数据提取助手。请为给定的文档片段提取主题与关键词。"
    "严格按 JSON 数组输出，每个元素形如 "
    '{"topic":"主题","keywords":"关键词1,关键词2"}。'
    "主题用不超过 10 个字概括；关键词 3-5 个，逗号分隔。"
    "不要输出任何解释文字，只输出 JSON 数组。"
)


class MetadataExtractor:
    """
    LLM 元数据提取器。

    Usage::

        extractor = MetadataExtractor()
        metas = extractor.extract_batch(parent_chunks)
        # metas[i] = {"topic": "...", "keywords": "..."}
    """

    def __init__(self) -> None:
        self._llm = None

    def _get_llm(self):
        if self._llm is None:
            from app.services.llm_client import get_llm_client
            self._llm = get_llm_client()
        return self._llm

    def extract_batch(
        self, parent_chunks: list[Any],
    ) -> list[dict[str, str]]:
        """批量提取父切块元数据，返回与输入等长的元数据列表。"""
        if not parent_chunks:
            return []

        if not settings.LLM_METADATA_ENABLED:
            return [{"topic": "", "keywords": ""} for _ in parent_chunks]

        batch = settings.LLM_METADATA_BATCH_SIZE
        results: list[dict[str, str]] = []

        for start in range(0, len(parent_chunks), batch):
            batch_chunks = parent_chunks[start:start + batch]
            batch_metas = self._extract_one_batch(batch_chunks)
            results.extend(batch_metas)

        while len(results) < len(parent_chunks):
            results.append({"topic": "", "keywords": ""})

        return results[:len(parent_chunks)]

    def _extract_one_batch(
        self, chunks: list[Any],
    ) -> list[dict[str, str]]:
        """对一批父切块调用 LLM 提取元数据。"""
        default = [{"topic": "", "keywords": ""} for _ in chunks]

        lines = []
        for i, c in enumerate(chunks):
            text = (c.content if hasattr(c, "content") else str(c))[:600]
            lines.append(f"[片段{i + 1}]\n{text}")
        user_msg = "\n\n".join(lines)

        messages = [
            {"role": "system", "content": _META_SYSTEM_PROMPT},
            {"role": "user", "content": user_msg},
        ]

        try:
            llm = self._get_llm()
            answer, _ = llm.chat(messages, temperature=0.0)
            parsed = self._parse_json_array(answer)
            if parsed:
                result = []
                for i in range(len(chunks)):
                    item = parsed[i] if i < len(parsed) else {}
                    result.append({
                        "topic": str(item.get("topic", ""))[:30],
                        "keywords": str(item.get("keywords", ""))[:100],
                    })
                return result
            logger.warning("LLM 元数据解析为空，回退默认值")
            return default
        except Exception as e:
            logger.warning("LLM 元数据提取失败，回退默认值: %s", e)
            return default

    def _parse_json_array(self, text: str) -> list[dict]:
        """从 LLM 输出中解析 JSON 数组（容错）。"""
        if not text:
            return []

        cleaned = re.sub(r"```(?:json)?\s*", "", text)
        cleaned = cleaned.replace("```", "").strip()

        try:
            data = json.loads(cleaned)
            if isinstance(data, list):
                return data
        except json.JSONDecodeError:
            pass

        match = re.search(r"\[.*?\]", cleaned, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group())
                if isinstance(data, list):
                    return data
            except json.JSONDecodeError:
                pass

        return []


_extractor: Optional[MetadataExtractor] = None


def get_metadata_extractor() -> MetadataExtractor:
    """Return cached MetadataExtractor singleton."""
    global _extractor
    if _extractor is None:
        _extractor = MetadataExtractor()
    return _extractor
