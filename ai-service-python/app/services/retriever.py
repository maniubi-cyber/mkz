"""
Hybrid Retriever — ES BM25 + Qdrant 向量双路召回 → RRF 融合

针对单一检索路径的召回盲区设计双路召回:
- 向量检索 (Qdrant): 擅长语义改写召回，但漏精确关键词
- 关键词检索 (ES BM25 + IK 分词): 擅长精确关键词命中，但漏语义改写
- RRF (Reciprocal Rank Fusion) 融合两路结果取 top-k
- 叠加元数据权限过滤（在 Qdrant payload / ES filter 层完成）

Pipeline::

    query
      ├─→ Embedder.embed_query() → Qdrant.query()         (向量结果，命中子块)
      ├─→ ESBM25Client.search()                           (ES BM25 关键词结果)
      ├─→ _rrf_fusion(vec_results, bm25_results, k=60)   (融合排序)
      └─→ _backtrace_parent(merged)                       (回溯父块提供完整上下文)
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from app.core.config import settings
from app.services.es_bm25 import ESBM25Client, get_es_bm25_client
from app.services.vector_store import VectorStore, get_vector_store

logger = logging.getLogger(__name__)


# ============================================================
# Result DTO
# ============================================================

class ScoredChunk:
    """A single search result after fusion + parent backtrace."""

    __slots__ = (
        "chunk_id", "content", "score", "document_id", "document_name",
        "chunk_index", "kb_id", "owner_id", "visibility", "org_id",
        "parent_id", "source",
    )

    def __init__(
        self,
        chunk_id: str,
        content: str,
        score: float,
        document_id: int,
        document_name: str,
        chunk_index: int,
        kb_id: int,
        owner_id: int,
        visibility: str,
        org_id: int,
        parent_id: str = "",
        source: str = "parent",
    ) -> None:
        self.chunk_id = chunk_id
        self.content = content
        self.score = score
        self.document_id = document_id
        self.document_name = document_name
        self.chunk_index = chunk_index
        self.kb_id = kb_id
        self.owner_id = owner_id
        self.visibility = visibility
        self.org_id = org_id
        self.parent_id = parent_id
        self.source = source


# ============================================================
# HybridRetriever
# ============================================================

class HybridRetriever:
    """
    双路召回: ES BM25 + Qdrant 向量 → RRF 融合 → 父块回溯。

    Usage::

        retriever = HybridRetriever()
        results = retriever.search(
            kb_id=1, query="知识库系统", top_k=5,
            user_id=10, role="USER", org_id=5,
        )
    """

    # RRF constant (standard value from literature)
    RRF_K: int = 60

    def __init__(
        self,
        vector_store: VectorStore | None = None,
        es_bm25: ESBM25Client | None = None,
    ) -> None:
        self._vector_store = vector_store or get_vector_store()
        self._es_bm25 = es_bm25 or get_es_bm25_client()
        self._embedder = None
        self._parent_store = None

    @property
    def embedder(self):
        if self._embedder is None:
            from app.services.embedder import get_embedder
            self._embedder = get_embedder()
        return self._embedder

    @property
    def parent_store(self):
        """懒加载父块存储（回溯用）。"""
        if self._parent_store is None:
            from app.services.langchain_rag_service import RedisParentChunkStore
            self._parent_store = RedisParentChunkStore()
        return self._parent_store

    # ---- Public API ----

    def search(
        self,
        kb_id: int,
        query: str,
        top_k: int = 5,
        alpha: float = 0.5,
        similarity_threshold: float | None = None,
        user_id: int = 0,
        role: str = "USER",
        org_id: int = 0,
    ) -> list[ScoredChunk]:
        """
        Execute hybrid search.

        Args:
            kb_id:                Knowledge base ID.
            query:                Search query text (已过 LLM 改写).
            top_k:                Number of results to return.
            alpha:                0=pure BM25, 0.5=hybrid, 1=pure vector.
            similarity_threshold: Minimum RRF score to include. None → 服务端配置
                                  RAG_SIMILARITY_THRESHOLD（RRF 量纲 ~0~0.016）。
            user_id:              Current user ID (0 = anonymous).
            role:                 USER or ADMIN.
            org_id:               User's org ID.

        Returns:
            List of ScoredChunk (content 已回溯父块)，按相关度降序。
        """
        if similarity_threshold is None:
            similarity_threshold = settings.RAG_SIMILARITY_THRESHOLD
        # ---- 1. Qdrant 向量检索（命中子块，权限过滤在 payload 层）----
        vec_results: list[dict[str, Any]] = []
        if alpha > 0.0:
            vec_results = self._vector_search(kb_id, query, top_k * 3, user_id, role, org_id)

        # ---- 2. ES BM25 关键词检索（含权限过滤）----
        bm25_results: list[dict[str, Any]] = []
        if alpha < 1.0:
            bm25_results = self._bm25_search(kb_id, query, top_k * 3, user_id, role, org_id)

        # ---- 3. RRF 融合 ----
        fused = self._rrf_fusion(vec_results, bm25_results, alpha)

        # ---- 4. 阈值 + top_k ----
        results = [c for c in fused if c.score >= similarity_threshold][:top_k]

        # ---- 5. 父块回溯（提供完整上下文）----
        results = self._backtrace_parent(results)

        logger.info(
            "Hybrid search: kb_id=%d, query='%s', alpha=%.2f, "
            "vec=%d, es_bm25=%d, fused=%d, final=%d",
            kb_id, query[:40], alpha,
            len(vec_results), len(bm25_results), len(fused), len(results),
        )

        return results

    # ============================================================
    # Private — Vector Search (Qdrant)
    # ============================================================

    def _vector_search(
        self, kb_id: int, query: str, top_k: int,
        user_id: int, role: str, org_id: int,
    ) -> list[dict[str, Any]]:
        """Qdrant 向量检索（命中子块，权限过滤在 payload 层）。"""
        try:
            q_vec = self.embedder.embed_query(query).tolist()
            return self._vector_store.query(
                kb_id=kb_id,
                query_embedding=q_vec,
                top_k=top_k,
                user_id=user_id,
                role=role,
                org_id=org_id,
            )
        except Exception as e:
            logger.warning("Vector search failed: %s", e)
            return []

    # ============================================================
    # Private — ES BM25 Search
    # ============================================================

    def _bm25_search(
        self, kb_id: int, query: str, top_k: int,
        user_id: int, role: str, org_id: int,
    ) -> list[dict[str, Any]]:
        """ES BM25 关键词检索（含 IK 分词 + 权限过滤）。"""
        results = self._es_bm25.search(
            kb_id=kb_id,
            query=query,
            top_k=top_k,
            user_id=user_id,
            role=role,
            org_id=org_id,
        )
        return results

    # ============================================================
    # Private — RRF Fusion
    # ============================================================

    def _rrf_fusion(
        self,
        vec_results: list[dict[str, Any]],
        bm25_results: list[dict[str, Any]],
        alpha: float,
    ) -> list[ScoredChunk]:
        """
        Reciprocal Rank Fusion.

        For each unique chunk, compute:
            score = alpha * vec_rrf + (1-alpha) * bm25_rrf
        where:
            rrf = 1 / (RRF_K + rank)
        """
        from collections import defaultdict

        rrf_scores: dict[str, float] = defaultdict(float)
        chunk_map: dict[str, ScoredChunk] = {}

        # --- Vector RRF ---
        for rank, item in enumerate(vec_results, start=1):
            rrf = 1.0 / (self.RRF_K + rank)
            chunk_id = item["id"]
            meta = item.get("metadata", {})
            rrf_scores[chunk_id] += alpha * rrf

            if chunk_id not in chunk_map:
                chunk_map[chunk_id] = self._build_scored_chunk(
                    chunk_id, item, item.get("content", ""),
                )

        # --- BM25 RRF ---
        for rank, item in enumerate(bm25_results, start=1):
            rrf = 1.0 / (self.RRF_K + rank)
            chunk_id = item["id"]
            meta = item.get("metadata", {})
            rrf_scores[chunk_id] += (1.0 - alpha) * rrf

            if chunk_id not in chunk_map:
                chunk_map[chunk_id] = self._build_scored_chunk(
                    chunk_id, item, item.get("content", ""),
                )

        # --- Merge scores ---
        results: list[ScoredChunk] = []
        for chunk_id, fused_score in rrf_scores.items():
            c = chunk_map[chunk_id]
            c.score = round(fused_score, 6)
            results.append(c)

        results.sort(key=lambda x: x.score, reverse=True)
        return results

    def _build_scored_chunk(
        self, chunk_id: str, item: dict[str, Any], content: str,
    ) -> ScoredChunk:
        """从检索结果构建 ScoredChunk。"""
        meta = item.get("metadata", {})
        return ScoredChunk(
            chunk_id=chunk_id,
            content=content,
            score=0.0,
            document_id=int(meta.get("document_id", 0)),
            document_name=str(meta.get("file_name", "")),
            chunk_index=int(meta.get("parent_index", meta.get("chunk_index", 0))),
            kb_id=int(meta.get("kb_id", 0)),
            owner_id=int(meta.get("owner_id", 0)),
            visibility=str(meta.get("visibility", "PRIVATE")),
            org_id=int(meta.get("org_id", 0)),
            parent_id=str(meta.get("parent_id", "")),
            source="child",
        )

    # ============================================================
    # Private — Parent Backtrace（命中子块后回溯父块）
    # ============================================================

    def _backtrace_parent(self, chunks: list[ScoredChunk]) -> list[ScoredChunk]:
        """
        通过 parent_id 回溯 Redis 中的父块，提供完整段落上下文。

        若父块缺失，退化使用子块内容。
        """
        parent_ids = [c.parent_id for c in chunks if c.parent_id]
        if not parent_ids:
            return chunks

        try:
            parents = self.parent_store.get_parent_chunks_by_ids(parent_ids)
            parent_map = {p.id: p for p in parents}
        except Exception as e:
            logger.warning("Parent backtrace failed: %s", e)
            return chunks

        for c in chunks:
            parent = parent_map.get(c.parent_id)
            if parent:
                c.content = parent.content
                c.source = "parent"
                c.chunk_index = parent.index

        return chunks


# ============================================================
# Module-level convenience
# ============================================================

_retriever: Optional[HybridRetriever] = None


def get_retriever() -> HybridRetriever:
    """Return a cached HybridRetriever instance."""
    global _retriever
    if _retriever is None:
        _retriever = HybridRetriever()
    return _retriever
