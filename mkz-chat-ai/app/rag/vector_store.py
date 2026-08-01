"""Chroma 向量库管理：封装多租户文档入库、相似度检索与删除。

使用 chromadb 原生 PersistentClient 与默认 embedding（ONNX MiniLM-L6-V2，
随 chromadb 自带，无需额外依赖 sentence-transformers / 外部 embedding 服务）。
多租户隔离通过 metadata 中的 user_id 字段在检索/删除时 where 过滤实现。
"""
import os
import uuid

import chromadb

from app.config.settings import get_settings

# uuid5 需要一个固定 namespace，保证同一 (user_id, doc_id, chunk_index) 生成同一 ID
_ID_NAMESPACE = uuid.UUID("00000000-0000-0000-0000-000000000000")


def _stable_id(user_id: str, doc_id: str, chunk_index: int) -> str:
    """基于 user_id+doc_id+chunk_index 生成幂等 ID，重复入库可覆盖。"""
    name = f"{user_id}::{doc_id}::{chunk_index}"
    return str(uuid.uuid5(_ID_NAMESPACE, name))


class VectorStoreManager:
    """Chroma 向量库管理：文档分块入库、相似度检索、删除。

    多租户隔离：所有向量 metadata 均带 user_id 与 doc_id，检索/删除时通过
    where 过滤限定到指定用户，避免跨租户数据泄漏。
    """

    def __init__(self) -> None:
        settings = get_settings()
        # 确保持久化目录存在（chromadb 不会自动创建）
        os.makedirs(settings.chroma_persist_directory, exist_ok=True)
        self._client = chromadb.PersistentClient(path=settings.chroma_persist_directory)
        # 复用同一 collection，多租户通过 metadata user_id 过滤
        # 使用 cosine 距离，更适合文本语义相似度
        self._collection = self._client.get_or_create_collection(
            name=settings.chroma_collection_name,
            metadata={"hnsw:space": "cosine"},
        )

    def upsert_chunks(self, user_id: str, doc_id: str, chunks: list[dict]) -> int:
        """文档分块入库。每个 chunk 含 title 和 content。

        metadata 带 user_id 和 doc_id，实现多租户隔离。
        用 uuid5(user_id+doc_id+chunk_index) 生成幂等 ID，重复入库即覆盖。
        返回入库分块数。
        """
        if not chunks:
            return 0
        ids = [_stable_id(user_id, doc_id, idx) for idx in range(len(chunks))]
        # 向量化文本拼接标题与内容，让标题语义参与 embedding
        documents = [f"{c['title']}\n{c['content']}" for c in chunks]
        metadatas = [
            {
                "user_id": user_id,
                "doc_id": doc_id,
                "chunk_index": idx,
                "title": c["title"],
                "content": c["content"],
            }
            for idx, c in enumerate(chunks)
        ]
        self._collection.upsert(ids=ids, documents=documents, metadatas=metadatas)
        return len(chunks)

    def search_chunks(self, user_id: str, query: str, top_k: int = 3) -> list[dict]:
        """相似度检索，按 user_id 过滤。返回 [{title, content, score}]。

        score 为 1 - cosine 距离，值越大越相关（范围约 [-1, 1]）。
        """
        if not query.strip():
            return []
        n_results = max(1, top_k)
        result = self._collection.query(
            query_texts=[query],
            n_results=n_results,
            where={"user_id": user_id},
        )
        documents = result.get("documents") or [[]]
        metadatas = result.get("metadatas") or [[]]
        distances = result.get("distances") or [[]]
        if not documents or not documents[0]:
            return []
        out: list[dict] = []
        for doc, meta, dist in zip(documents[0], metadatas[0], distances[0]):
            meta = meta or {}
            title = meta.get("title", "")
            # 优先用 metadata 中的原始 content，兜底用向量化文本
            content = meta.get("content", doc)
            score = 1.0 - float(dist)
            out.append({"title": title, "content": content, "score": score})
        return out

    def delete_by_doc(self, user_id: str, doc_id: str) -> int:
        """按 doc_id 删除该文档所有向量，返回删除数量。

        同时限定 user_id，确保多租户安全（doc_id 在跨用户场景下可能重名）。
        """
        existing = self._collection.get(
            where={"$and": [{"user_id": user_id}, {"doc_id": doc_id}]}
        )
        ids = existing.get("ids") or []
        if not ids:
            return 0
        self._collection.delete(ids=ids)
        return len(ids)
