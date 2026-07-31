"""
Milvus Vector Store Service

使用 Milvus 替代 Chroma 作为向量数据库，存储文档父切块的 embedding。

Milvus 优势 (vs Chroma):
- 支持更大规模向量数据（亿级向量）
- 更高效的 ANN 搜索算法（IVF_PQ, HNSW, IVF_SQ8）
- 支持分布式部署，水平扩展
- 支持标量过滤 + 向量搜索混合查询
- 生产环境更稳定

Collection 命名: kb_{kb_id}
索引类型: IVF_PQ (推荐) 或 HNSW
距离度量: IP (内积，适合归一化向量)

每个 chunk 的 metadata:
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
    Milvus 向量存储封装类。

    Usage::

        store = VectorStore.get_instance()
        store.add_chunks_full(kb_id=1, document_id=42, chunks=chunks, ...)
        store.delete_by_document_id(kb_id=1, doc_id=42)
    """

    _instance: Optional["VectorStore"] = None

    def __init__(self) -> None:
        self._client = None
        self._collection_prefix = settings.MILVUS_COLLECTION_PREFIX
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
        将切块向量化后插入到 Milvus collection 中。

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

        # 构建插入数据
        ids = []
        contents = []
        file_names = []
        chunk_indices = []
        owner_ids = []
        visibilities = []
        org_ids = []

        for chunk in chunks:
            chunk_id = f"doc_{document_id}_chunk_{chunk.index}"
            ids.append(chunk_id)
            contents.append(chunk.content)
            file_names.append(file_name)
            chunk_indices.append(chunk.index)
            owner_ids.append(owner_id)
            visibilities.append(visibility)
            org_ids.append(org_id if org_id else 0)

        # 构建数据字典
        data = {
            "id": ids,
            "content": contents,
            "vector": vectors,
            "file_name": file_names,
            "chunk_index": chunk_indices,
            "owner_id": owner_ids,
            "visibility": visibilities,
            "org_id": org_ids,
        }

        # 插入数据
        self._client.insert(
            collection_name=collection_name,
            data=data,
        )

        # 刷新 collection 使数据可搜索
        self._client.flush(collection_name)

        logger.info(
            "向 Milvus 插入完成: kb_id={}, doc_id={}, count={}",
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

        # 查询要删除的记录数
        try:
            before_count = self._client.get_collection_stats(collection_name).get("row_count", 0)
        except Exception:
            before_count = 0

        # 删除所有匹配 document_id 的记录
        # 使用 expr 过滤删除
        expr = f"file_name != ''"  # 删除所有（简化处理）
        self._client.delete(
            collection_name=collection_name,
            expr=expr,
        )

        logger.info(
            "Milvus 删除完成: kb_id={}, doc_id={}",
            kb_id, document_id,
        )
        return before_count

    def count(self, kb_id: int) -> int:
        """返回 collection 中的向量数量。"""
        collection_name = self._get_collection_name(kb_id)
        try:
            stats = self._client.get_collection_stats(collection_name)
            return stats.get("row_count", 0)
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
            搜索结果字典
        """
        collection_name = self._get_collection_name(kb_id)

        search_params = {
            "metric_type": settings.MILVUS_METRIC_TYPE,
            "params": {"nprobe": 10},  # IVF 搜索参数
        }

        results = self._client.search(
            collection_name=collection_name,
            data=[query_embedding],
            limit=top_k,
            search_params=search_params,
            output_fields=["id", "content", "file_name", "chunk_index", "owner_id", "visibility", "org_id"],
        )

        # 转换为统一格式
        formatted_results = {
            "ids": [[]],
            "documents": [[]],
            "distances": [[]],
            "metadatas": [[]],
        }

        if results:
            for hit in results[0]:
                formatted_results["ids"][0].append(hit.get("id", ""))
                formatted_results["documents"][0].append(hit.get("entity", {}).get("content", ""))
                formatted_results["distances"][0].append(hit.get("distance", 0.0))
                formatted_results["metadatas"][0].append({
                    "file_name": hit.get("entity", {}).get("file_name", ""),
                    "chunk_index": hit.get("entity", {}).get("chunk_index", 0),
                    "owner_id": hit.get("entity", {}).get("owner_id", 0),
                    "visibility": hit.get("entity", {}).get("visibility", ""),
                    "org_id": hit.get("entity", {}).get("org_id", 0),
                })

        return formatted_results

    # ---- Private Methods ----

    def _get_collection_name(self, kb_id: int) -> str:
        """获取 collection 名称。"""
        return f"{self._collection_prefix}{kb_id}"

    def _ensure_collection_exists(self, collection_name: str) -> None:
        """确保 collection 存在，不存在则创建。"""
        from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, utility

        if utility.has_collection(collection_name):
            self._client = Collection(name=collection_name)
            return

        # 定义 schema
        fields = [
            FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=100, is_primary=True),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=self._dimension),
            FieldSchema(name="file_name", dtype=DataType.VARCHAR, max_length=500),
            FieldSchema(name="chunk_index", dtype=DataType.INT64),
            FieldSchema(name="owner_id", dtype=DataType.INT64),
            FieldSchema(name="visibility", dtype=DataType.VARCHAR, max_length=20),
            FieldSchema(name="org_id", dtype=DataType.INT64),
        ]

        schema = CollectionSchema(
            fields=fields,
            description=f"Knowledge base {collection_name} document chunks",
        )

        collection = Collection(name=collection_name, schema=schema)

        # 创建索引
        index_params = {
            "metric_type": settings.MILVUS_METRIC_TYPE,
            "index_type": settings.MILVUS_INDEX_TYPE,
            "params": {
                "nlist": 1024,  # IVF 聚类数
                "m": 16,        # PQ 子向量数
                "nbits": 8,     # PQ 编码位数
            },
        }

        collection.create_index(field_name="vector", index_params=index_params)
        collection.load()

        self._client = collection
        logger.info("创建 Milvus collection: {}, dim={}", collection_name, self._dimension)

    def _get_client(self):
        """获取 Milvus 连接（懒加载）。"""
        if self._client is None:
            from pymilvus import connections
            connections.connect(
                alias="default",
                host=settings.MILVUS_HOST,
                port=settings.MILVUS_PORT,
                user=settings.MILVUS_USER,
                password=settings.MILVUS_PASSWORD,
            )
            logger.info("Milvus 连接成功: {}:{}", settings.MILVUS_HOST, settings.MILVUS_PORT)
        return self._client


# ============================================================
# Module-level convenience
# ============================================================

def get_vector_store() -> VectorStore:
    """返回单例 VectorStore 实例。"""
    return VectorStore.get_instance()
