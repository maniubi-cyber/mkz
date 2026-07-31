"""
Chroma Vector Store Service

使用 Chroma 作为向量数据库，存储文档父切块的 embedding。

Chroma 优势:
- 嵌入式向量数据库，无需独立服务部署
- 支持持久化存储到磁盘
- 内置 HNSW 索引，搜索效率高
- REST API + Python SDK 双协议
- 适合中小规模知识库（万~百万级向量）

Collection 命名: kb_{kb_id}
距离度量: cosine（默认，适合归一化向量）

每个文档的 metadata:
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
    Chroma 向量存储封装类。

    Usage::

        store = VectorStore.get_instance()
        store.add_chunks_full(kb_id=1, document_id=42, chunks=chunks, ...)
        store.delete_by_document_id(kb_id=1, doc_id=42)
    """

    _instance: Optional["VectorStore"] = None

    def __init__(self) -> None:
        self._client = None
        self._collection_prefix = settings.CHROMA_COLLECTION_PREFIX
        self._dimension = settings.EMBEDDING_DIMENSION
        self._persist_dir = settings.CHROMA_PERSIST_DIR

    # ---- Singleton ----

    @classmethod
    def get_instance(cls) -> "VectorStore":
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    # ---- Internal: Collection Name ----

    def _get_collection_name(self, kb_id: int) -> str:
        """获取 collection 名称。"""
        return f"{self._collection_prefix}{kb_id}"

    # ---- Internal: Client Lazy-Load ----

    def _get_client(self):
        """获取 Chroma 客户端（懒加载）。"""
        if self._client is None:
            import chromadb
            self._client = chromadb.PersistentClient(
                path=self._persist_dir,
            )
            logger.info(
                "Chroma 客户端初始化完成: path=%s",
                self._persist_dir,
            )
        return self._client

    # ---- Collection Management ----

    def _ensure_collection_exists(self, collection_name: str):
        """确保 collection 存在，不存在则创建。"""
        client = self._get_client()
        try:
            return client.get_collection(name=collection_name)
        except Exception:
            # Collection 不存在，创建
            import chromadb
            return client.create_collection(
                name=collection_name,
                metadata={
                    "hnsw:space": "cosine",
                    "hnsw:M": 16,
                    "hnsw:C_m": 2,
                },
            )

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
        将切块向量化后插入到 Chroma collection 中。

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
        collection = self._ensure_collection_exists(collection_name)

        from app.services.embedder import get_embedder

        embedder = get_embedder()

        # 批量 embedding
        texts = [c.content for c in chunks]
        vectors = [vec.tolist() for vec in embedder.embed(texts)]

        # 构建 ID 列表和 metadata
        ids = []
        metadatas = []
        documents = []

        for chunk in chunks:
            chunk_id = f"doc_{document_id}_chunk_{chunk.index}"
            ids.append(chunk_id)
            metadatas.append({
                "document_id": document_id,
                "kb_id": kb_id,
                "file_name": file_name,
                "chunk_index": chunk.index,
                "owner_id": owner_id,
                "visibility": visibility,
                "org_id": org_id if org_id else 0,
            })
            documents.append(chunk.content)

        # 批量 upsert（Chroma 按 id 自动去重）
        collection.upsert(
            ids=ids,
            embeddings=vectors,
            metadatas=metadatas,
            documents=documents,
        )

        logger.info(
            "向 Chroma 插入完成: kb_id={}, doc_id={}, count={}",
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
            删除的记录数
        """
        collection_name = self._get_collection_name(kb_id)
        collection = self._ensure_collection_exists(collection_name)

        # 查询该文档的所有向量
        try:
            result = collection.get(
                where={"document_id": document_id},
                include=["metadatas"],
            )
            ids_to_delete = result["ids"] if result and "ids" in result else []

            if ids_to_delete:
                collection.delete(ids=ids_to_delete)
                logger.info(
                    "Chroma 删除完成: kb_id={}, doc_id={}, deleted={}",
                    kb_id, document_id, len(ids_to_delete),
                )
            return len(ids_to_delete)
        except Exception as e:
            logger.error("Chroma 删除失败: kb_id={}, doc_id={}, error={}",
                         kb_id, document_id, e)
            return 0

    def count(self, kb_id: int) -> int:
        """返回 collection 中的向量数量。"""
        collection_name = self._get_collection_name(kb_id)
        try:
            collection = self._ensure_collection_exists(collection_name)
            return collection.count()
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
            top_k:           返回结果数量

        Returns:
            搜索结果字典（与检索器兼容的统一格式）
        """
        collection_name = self._get_collection_name(kb_id)
        collection = self._ensure_collection_exists(collection_name)

        results = collection.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
            include=["metadatas", "documents", "distances"],
        )

        # 转换为与检索器兼容的统一格式
        # Chroma 返回 cosine 距离 = 1 - similarity
        formatted_results = {
            "ids": [[]],
            "documents": [[]],
            "distances": [[]],
            "metadatas": [[]],
        }

        if results and results["ids"] and results["ids"][0]:
            ids = results["ids"][0]
            docs = results.get("documents", [[]])[0]
            dists = results.get("distances", [[]])[0]
            metas = results.get("metadatas", [[]])[0]

            for i in range(len(ids)):
                distance = dists[i] if i < len(dists) else 1.0
                formatted_results["ids"][0].append(ids[i])
                formatted_results["documents"][0].append(docs[i] if i < len(docs) else "")
                # Chroma cosine distance (0~1, smaller = more similar)
                formatted_results["distances"][0].append(distance)
                formatted_results["metadatas"][0].append(
                    metas[i] if i < len(metas) else {}
                )

        return formatted_results

    def delete_collection(self, kb_id: int) -> bool:
        """删除指定知识库的整个 collection。"""
        client = self._get_client()
        collection_name = self._get_collection_name(kb_id)
        try:
            client.delete_collection(name=collection_name)
            logger.info("Chroma collection 已删除: {}", collection_name)
            return True
        except Exception as e:
            logger.warning("Chroma collection 删除失败: {}, error={}",
                           collection_name, e)
            return False


# ============================================================
# Module-level convenience
# ============================================================

def get_vector_store() -> VectorStore:
    """返回单例 VectorStore 实例。"""
    return VectorStore.get_instance()
