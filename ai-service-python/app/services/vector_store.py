"""
Qdrant Vector Store Service — Lightweight Version

使用 Qdrant 作为向量数据库，存储文档父切块的 embedding。

Qdrant 优势 (vs Chroma):
- 轻量级部署，单二进制无需 etcd/MinIO 依赖
- 丰富的过滤条件（payload 过滤 + 向量相似度）
- 生产环境稳定，支持分布式扩展
- REST + gRPC 双协议，Python SDK 成熟

Collection 命名: kb_{kb_id}
向量维度: 由 EMBEDDING_DIMENSION 配置决定
距离度量: COSINE（默认，适合归一化向量）

每个点 (Point) 的 payload:
    document_id   (int)    — 源文档主键
    kb_id         (int)    — 知识库主键
    file_name     (str)    — 原始文件名
    chunk_index   (int)    — 文档内切片序号（从 0 开始）
    owner_id      (int)    — 上传者用户 ID
    visibility    (str)    — PRIVATE | PUBLIC | ORG
    org_id        (int)    — 组织 ID（0 表示不适用）
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from app.core.config import settings
from app.services.chunker import Chunk

logger = logging.getLogger(__name__)


class VectorStore:
    """
    Qdrant 向量存储封装类。

    Usage::

        store = VectorStore.get_instance()
        store.add_chunks_full(kb_id=1, document_id=42, chunks=chunks, ...)
        store.delete_by_document_id(kb_id=1, doc_id=42)
    """

    _instance: Optional["VectorStore"] = None

    def __init__(self) -> None:
        self._client = None
        self._collection_prefix = settings.QDRANT_COLLECTION_PREFIX
        self._dimension = settings.EMBEDDING_DIMENSION

    # ---- Singleton ----

    @classmethod
    def get_instance(cls) -> "VectorStore":
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    # ---- Public API ----

    def add_chunks_full(
        self,
        kb_id: int,
        document_id: int,
        chunks: list[Chunk],
        file_name: str,
        owner_id: int,
        visibility: str,
        org_id: int,
    ) -> int:
        """
        将切块向量化后插入到 Qdrant collection 中。

        Args:
            kb_id:       知识库 ID
            document_id: 文档主键
            chunks:      切块对象列表（包含 content 和 index）
            file_name:   原始文件名
            owner_id:    上传者用户 ID
            visibility:  权限范围 (PRIVATE/PUBLIC/ORG)
            org_id:      组织 ID

        Returns:
            插入的切块数量
        """
        if not chunks:
            return 0

        collection_name = self._get_collection_name(kb_id)
        self._ensure_collection_exists(collection_name)

        from app.services.embedder import get_embedder

        embedder = get_embedder()

        # 批量 embedding
        texts = [c.content for c in chunks]
        vectors = [vec.tolist() for vec in embedder.embed(texts)]

        # 构建 Qdrant 点列表
        from qdrant_client.models import PointStruct

        points = []
        for i, chunk in enumerate(chunks):
            point_id = f"doc_{document_id}_chunk_{chunk.index}"
            points.append(
                PointStruct(
                    id=point_id,
                    vector=vectors[i],
                    payload={
                        "content": chunk.content,
                        "file_name": file_name,
                        "chunk_index": chunk.index,
                        "document_id": document_id,
                        "owner_id": owner_id,
                        "visibility": visibility,
                        "org_id": org_id if org_id else 0,
                    },
                )
            )

        # 批量上载
        self._client.upsert(
            collection_name=collection_name,
            points=points,
        )

        logger.info(
            "向 Qdrant 插入完成: kb_id={}, doc_id={}, count={}",
            kb_id, document_id, len(chunks),
        )
        return len(chunks)

    def delete_by_document_id(self, kb_id: int, document_id: int) -> int:
        """
        删除指定文档的所有切块向量。

        Args:
            kb_id:       知识库 ID
            document_id: 文档主键

        Returns:
            删除的记录数（估计值）
        """
        collection_name = self._get_collection_name(kb_id)

        from qdrant_client.models import Filter, FieldCondition, MatchValue

        # 使用过滤器删除指定 document_id 的所有点
        self._client.delete(
            collection_name=collection_name,
            points_selector=Filter(
                must=[
                    FieldCondition(
                        key="document_id",
                        match=MatchValue(value=document_id),
                    )
                ]
            ),
        )

        logger.info(
            "Qdrant 删除完成: kb_id={}, doc_id={}",
            kb_id, document_id,
        )
        return 0

    def count(self, kb_id: int) -> int:
        """返回 collection 中的向量数量。"""
        collection_name = self._get_collection_name(kb_id)
        try:
            count_result = self._client.count(collection_name=collection_name)
            return count_result.count
        except Exception:
            return 0

    def query(
        self,
        kb_id: int,
        query_embedding: list[float],
        top_k: int = 5,
    ) -> dict[str, Any]:
        """
        向量相似度搜索。

        Args:
            kb_id:           知识库 ID
            query_embedding: 查询向量
            top_k:          返回结果数量

        Returns:
            搜索结果字典（与检索器兼容的统一格式）
        """
        collection_name = self._get_collection_name(kb_id)

        results = self._client.search(
            collection_name=collection_name,
            query_vector=query_embedding,
            limit=top_k,
        )

        # 转换为与检索器兼容的统一格式
        formatted_results = {
            "ids": [[]],
            "documents": [[]],
            "distances": [[]],
            "metadatas": [[]],
        }

        if results:
            for hit in results:
                payload = hit.payload or {}
                # Qdrant score 是相似度分数（0~1），转换为距离（1-score 表示距离）
                distance = 1.0 - hit.score if hit.score else 0.0
                formatted_results["ids"][0].append(hit.id)
                formatted_results["documents"][0].append(payload.get("content", ""))
                formatted_results["distances"][0].append(distance)
                formatted_results["metadatas"][0].append({
                    "file_name": payload.get("file_name", ""),
                    "chunk_index": payload.get("chunk_index", 0),
                    "owner_id": payload.get("owner_id", 0),
                    "visibility": payload.get("visibility", ""),
                    "org_id": payload.get("org_id", 0),
                    "document_id": payload.get("document_id", 0),
                })

        return formatted_results

    # ---- Private Methods ----

    def _get_collection_name(self, kb_id: int) -> str:
        """获取 collection 名称。"""
        return f"{self._collection_prefix}{kb_id}"

    def _ensure_collection_exists(self, collection_name: str) -> None:
        """确保 collection 存在，不存在则创建。"""
        from qdrant_client.models import Distance, VectorParams

        if self._client.collection_exists(collection_name):
            return

        self._client.create_collection(
            collection_name=collection_name,
            vectors_config=VectorParams(
                size=self._dimension,
                distance=Distance.COSINE,
            ),
        )

        logger.info(
            "创建 Qdrant collection: {}, dim={}",
            collection_name, self._dimension,
        )

    def _get_client(self):
        """获取 Qdrant 连接（懒加载）。"""
        if self._client is None:
            from qdrant_client import QdrantClient
            self._client = QdrantClient(
                host=settings.QDRANT_HOST,
                port=settings.QDRANT_PORT,
                username=settings.QDRANT_USER,
                password=settings.QDRANT_PASSWORD,
                https=False,
            )
            logger.info(
                "Qdrant 连接成功: {}:{:d}",
                settings.QDRANT_HOST, settings.QDRANT_PORT,
            )
        return self._client


# ============================================================
# Module-level convenience
# ============================================================

def get_vector_store() -> VectorStore:
    """返回单例 VectorStore 实例。"""
    return VectorStore.get_instance()
