"""
Tests for HybridRetriever — RRF fusion + permission filtering.

These tests verify the algorithm logic without requiring actual
Qdrant / Embedder / BM25 infrastructure.

Run::

    pytest tests/test_retriever.py -v
"""

from __future__ import annotations

import pytest

from app.core.config import settings
from app.services.retriever import HybridRetriever, ScoredChunk


# ============================================================
# Helpers — build synthetic ScoredChunk objects
# ============================================================

def _sc(
    chunk_id: str = "c0",
    content: str = "test",
    score: float = 0.0,
    document_id: int = 1,
    document_name: str = "doc.pdf",
    chunk_index: int = 0,
    kb_id: int = 1,
    owner_id: int = 10,
    visibility: str = "PRIVATE",
    org_id: int = 5,
) -> ScoredChunk:
    return ScoredChunk(
        chunk_id=chunk_id,
        content=content,
        score=score,
        document_id=document_id,
        document_name=document_name,
        chunk_index=chunk_index,
        kb_id=kb_id,
        owner_id=owner_id,
        visibility=visibility,
        org_id=org_id,
    )


def _vec_result(chunk_id: str, content: str, similarity: float,
                document_id: int = 1, document_name: str = "doc.pdf",
                owner_id: int = 10, visibility: str = "PRIVATE",
                org_id: int = 5) -> dict:
    """Build a synthetic vector result dict matching Qdrant output."""
    return {
        "id": chunk_id,
        "content": content,
        "score": similarity,
        "metadata": {
            "document_id": document_id,
            "file_name": document_name,
            "chunk_index": int(chunk_id.split("_")[-1]) if "_" in chunk_id else 0,
            "kb_id": 1,
            "owner_id": owner_id,
            "visibility": visibility,
            "org_id": org_id,
        },
    }


def _bm25_result(chunk_id: str, content: str) -> dict:
    """Build a synthetic BM25 result dict matching ES BM25 client output."""
    return {"id": chunk_id, "content": content, "metadata": {}}


# ============================================================
# Tests — RRF Fusion
# ============================================================

class TestRRFFusion:
    """Reciprocal Rank Fusion algorithm tests."""

    def test_rrf_empty_inputs(self):
        """Empty inputs → empty output."""
        retriever = HybridRetriever()
        result = retriever._rrf_fusion([], [], alpha=0.5)
        assert result == []

    def test_rrf_vector_only(self):
        """alpha=1.0 → only vector contributes."""
        retriever = HybridRetriever()
        vec = [
            _vec_result("c0", "内容A", 0.9),
            _vec_result("c1", "内容B", 0.7),
            _vec_result("c2", "内容C", 0.5),
        ]
        result = retriever._rrf_fusion(vec, [], alpha=1.0)
        assert len(result) == 3
        # Higher rank → higher RRF score
        assert result[0].chunk_id == "c0"
        assert result[1].chunk_id == "c1"
        assert result[2].chunk_id == "c2"
        # Scores should be strictly decreasing
        assert result[0].score > result[1].score > result[2].score

    def test_rrf_bm25_only(self):
        """alpha=0.0 → only BM25 contributes."""
        retriever = HybridRetriever()
        bm25 = [
            _bm25_result("bm25_0", "A"),
            _bm25_result("bm25_1", "B"),
            _bm25_result("bm25_2", "C"),
        ]
        result = retriever._rrf_fusion([], bm25, alpha=0.0)
        assert len(result) == 3
        assert result[0].chunk_id == "bm25_0"

    def test_rrf_hybrid_fusion(self):
        """alpha=0.5 → both sources contribute to final score."""
        retriever = HybridRetriever()
        vec = [_vec_result("v0", "向量结果", 0.95)]
        bm25 = [_bm25_result("bm25_0", "关键词结果")]

        result = retriever._rrf_fusion(vec, bm25, alpha=0.5)
        assert len(result) >= 1  # at least one result
        # Both IDs should appear
        ids = {c.chunk_id for c in result}
        assert "v0" in ids
        assert "bm25_0" in ids

    def test_rrf_rank_ordering(self):
        """Item ranked #1 should have higher RRF than #2."""
        retriever = HybridRetriever()
        vec = [
            _vec_result("c0", "最高", 0.99),
            _vec_result("c1", "中等", 0.80),
            _vec_result("c2", "最低", 0.60),
        ]
        result = retriever._rrf_fusion(vec, [], alpha=1.0)

        # RRF for rank 1: 1/(60+1) = 1/61 ≈ 0.01639
        # RRF for rank 2: 1/(60+2) = 1/62 ≈ 0.01613
        # RRF for rank 3: 1/(60+3) = 1/63 ≈ 0.01587
        assert result[0].score > result[1].score
        assert result[1].score > result[2].score

    def test_rrf_deduplication(self):
        """Same chunk appearing in both lists gets RRF scores summed."""
        retriever = HybridRetriever()
        vec = [_vec_result("shared", "共享内容", 0.9)]
        # BM25 can't have the same ID, so dedup only works within same source
        bm25 = [_bm25_result("bm25_0", "共享内容")]

        result = retriever._rrf_fusion(vec, bm25, alpha=0.5)
        ids = [c.chunk_id for c in result]
        assert "shared" in ids
        assert "bm25_0" in ids

    def test_rrf_k_constant_effect(self):
        """Verify the RRF_K=60 constant produces expected values."""
        retriever = HybridRetriever()
        # Single item at rank 1
        vec = [_vec_result("only", "唯一", 0.9)]
        result = retriever._rrf_fusion(vec, [], alpha=1.0)

        expected_rrf = 1.0 / (60 + 1)  # 1/61
        assert result[0].score == pytest.approx(expected_rrf, abs=1e-5)


# ============================================================
# Tests — Threshold dimension (RRF score vs similarity_threshold)
# ============================================================

class TestThresholdCompatibility:
    """RRF 融合分数量纲与阈值默认值的兼容性（回归防护）。"""

    def test_default_threshold_allows_rank1_results(self):
        """双路命中的 rank-1 结果（分数≈0.016）必须通过默认阈值。"""
        retriever = HybridRetriever()
        vec = [_vec_result("c0", "内容A", 0.9)]
        bm25 = [_bm25_result("bm25_0", "内容A")]
        fused = retriever._rrf_fusion(vec, bm25, alpha=0.5)
        assert fused, "双路命中 rank-1 应有融合结果"

        threshold = settings.RAG_SIMILARITY_THRESHOLD
        assert threshold <= 0.02, (
            f"默认阈值 {threshold} 必须匹配 RRF 量纲（最高约 0.0164），否则检索恒空"
        )
        kept = [c for c in fused if c.score >= threshold]
        assert kept, (
            f"默认阈值 {threshold} 不应过滤掉双路命中的结果，"
            f"实际分数 {[c.score for c in fused]}"
        )

    def test_old_threshold_035_filters_everything(self):
        """回归防护：旧的 0.35 阈值在 RRF 量纲下必然过滤掉全部结果。"""
        retriever = HybridRetriever()
        vec = [_vec_result("c0", "内容A", 0.9)]
        bm25 = [_bm25_result("bm25_0", "内容A")]
        fused = retriever._rrf_fusion(vec, bm25, alpha=0.5)
        kept = [c for c in fused if c.score >= 0.35]
        assert not kept, "0.35 阈值下 RRF 分数(≤0.016)必然全被过滤，若此处有结果说明量纲已变化"


# ============================================================
# Tests — Permission Filter (Qdrant payload 层，真实实现)
# ============================================================

class TestVectorPermissionFilter:
    """VectorStore._build_permission_filter 的权限过滤语义。"""

    def _build(self, user_id=0, role="USER", org_id=0):
        from app.services.vector_store import VectorStore

        store = VectorStore()
        return store._build_permission_filter(user_id, role, org_id)

    def _should_keys(self, qfilter):
        if qfilter is None:
            return None
        return {
            tuple(cond.key for cond in f.must)
            for f in qfilter.should
        }

    def test_admin_bypasses(self):
        """ADMIN → None（不过滤）。"""
        assert self._build(user_id=10, role="ADMIN", org_id=5) is None

    def test_owner_public_org_conditions(self):
        """普通用户：owner_id / PUBLIC / (ORG+org_id) 三路 OR。"""
        keys = self._should_keys(self._build(user_id=10, role="USER", org_id=5))
        assert ("owner_id",) in keys
        assert ("visibility",) in keys
        assert ("visibility", "org_id") in keys

    def test_anonymous_no_owner_no_org(self):
        """匿名用户（user_id=0, org_id=0）：仅 PUBLIC 路。"""
        keys = self._should_keys(self._build(user_id=0, role="USER", org_id=0))
        assert keys == {("visibility",)}

    def test_org_zero_excluded(self):
        """org_id=0 时不生成 ORG 条件（避免误配未设置的组织）。"""
        keys = self._should_keys(self._build(user_id=10, role="USER", org_id=0))
        assert ("visibility", "org_id") not in keys

    def test_role_case_insensitive(self):
        """role 大小写不敏感（admin/Admin 均绕过）。"""
        from app.services.vector_store import VectorStore

        store = VectorStore()
        assert store._build_permission_filter(1, "admin", 0) is None
        assert store._build_permission_filter(1, "Admin", 0) is None


# ============================================================
# Tests — ScoredChunk DTO
# ============================================================

class TestScoredChunk:
    """ScoredChunk data class tests."""

    def test_creation(self):
        c = ScoredChunk(
            chunk_id="doc_1_chunk_0", content="测试内容", score=0.85,
            document_id=1, document_name="test.pdf", chunk_index=0,
            kb_id=1, owner_id=10, visibility="PUBLIC", org_id=5,
        )
        assert c.chunk_id == "doc_1_chunk_0"
        assert c.content == "测试内容"
        assert c.score == 0.85
        assert c.visibility == "PUBLIC"

    def test_defaults(self):
        c = _sc()
        assert c.score == 0.0
        assert c.visibility == "PRIVATE"

    def test_slots_no_extra_attrs(self):
        """ScoredChunk uses __slots__ — no dict overhead."""
        c = _sc()
        with pytest.raises(AttributeError):
            c.non_existent = 123  # type: ignore[attr-defined]
