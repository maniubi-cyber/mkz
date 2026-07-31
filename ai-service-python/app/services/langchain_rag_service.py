"""
LangChain RAG Service - 基于 LangChain 的检索增强生成服务

核心设计:
- 文档元数据: MySQL
- 父切块 (Parent Chunks): Chroma 向量数据库存储
- 子切块 (Child Chunks): Redis 缓存存储

Chroma 优势:
- 轻量级部署，单二进制无需 etcd/MinIO 依赖
- 丰富的 payload 过滤条件
- 生产环境稳定，支持分布式扩展

父子切块策略:
1. 文档被切成较大的父切块（1000字符），每个父切块生成向量存入 Chroma
2. 每个父切块再细分为子切块（200字符），存入 Redis 缓存
3. 检索时先通过向量检索找到相关父切块，再从 Redis 获取子切块进行精确匹配
4. 最终将最相关的子切块作为上下文送入 LLM 生成答案
"""

from __future__ import annotations

import json
import logging
from typing import Any, Optional
from dataclasses import dataclass, field

from app.core.config import settings

logger = logging.getLogger(__name__)


# ============================================================
# Data Classes
# ============================================================

@dataclass
class ChildChunk:
    """子切块 - 存储在 Redis 中"""
    id: str
    parent_id: str  # 所属父切块 ID
    document_id: int
    kb_id: int
    content: str
    chunk_index: int
    metadata: dict = field(default_factory=dict)


@dataclass
class ParentChunk:
    """父切块 - 存储在 Chroma 向量数据库中"""
    id: str
    document_id: int
    kb_id: int
    content: str
    child_chunk_ids: list[str] = field(default_factory=list)
    metadata: dict = field(default_factory=dict)


@dataclass
class RetrievalResult:
    """检索结果"""
    content: str
    score: float
    source: str  # parent / child
    document_id: int
    chunk_index: int
    metadata: dict = field(default_factory=dict)


# ============================================================
# Parent-Child Chunker
# ============================================================

class ParentChildChunker:
    """
    父子切块器

    将文档内容切分为父切块和子切块:
    1. 首先按较大尺寸切块（父切块）
    2. 每个父切块再细分为更小的子切块

    Key design:
    - 父切块: 1000字符，保持完整段落结构，用于向量检索
    - 子切块: 200字符，精确匹配，用于 LLM 输入
    """

    def __init__(
        self,
        parent_chunk_size: int = None,
        child_chunk_size: int = None,
        overlap: int = None,
    ):
        self.parent_chunk_size = parent_chunk_size or settings.PARENT_CHUNK_SIZE
        self.child_chunk_size = child_chunk_size or settings.CHILD_CHUNK_SIZE
        self.overlap = overlap or settings.CHUNK_OVERLAP

    def chunk_document(
        self, document_id: int, kb_id: int, content: str
    ) -> tuple[list[ParentChunk], list[ChildChunk]]:
        """
        将文档内容切分为父切块和子切块

        Args:
            document_id: 文档 ID
            kb_id: 知识库 ID
            content: 文档内容

        Returns:
            (父切块列表, 子切块列表)
        """
        if not content or not content.strip():
            return [], []

        # Phase 1: 生成父切块
        parent_texts = self._split_into_parent_chunks(content)

        parent_chunks = []
        all_child_chunks = []

        for i, parent_text in enumerate(parent_texts):
            parent_id = f"doc_{document_id}_parent_{i}"

            # Phase 2: 将父切块细分为子切块
            child_texts = self._split_into_child_chunks(parent_text)

            child_chunks = []
            for j, child_text in enumerate(child_texts):
                child_id = f"doc_{document_id}_child_{i}_{j}"
                child_chunk = ChildChunk(
                    id=child_id,
                    parent_id=parent_id,
                    document_id=document_id,
                    kb_id=kb_id,
                    content=child_text,
                    chunk_index=j,
                    metadata={
                        "parent_index": i,
                        "child_index": j,
                    }
                )
                child_chunks.append(child_chunk)

            parent_chunk = ParentChunk(
                id=parent_id,
                document_id=document_id,
                kb_id=kb_id,
                content=parent_text,
                child_chunk_ids=[c.id for c in child_chunks],
                metadata={
                    "parent_index": i,
                    "child_count": len(child_chunks),
                }
            )
            parent_chunks.append(parent_chunk)
            all_child_chunks.extend(child_chunks)

        logger.info(
            "父子切块完成: doc_id={}, kb_id={}, 父切块数={}, 子切块数={}",
            document_id, kb_id, len(parent_chunks), len(all_child_chunks),
        )

        return parent_chunks, all_child_chunks

    def _split_into_parent_chunks(self, text: str) -> list[str]:
        """将文本切分为父切块"""
        return self._smart_split(text, self.parent_chunk_size)

    def _split_into_child_chunks(self, text: str) -> list[str]:
        """将父切块文本细分为子切块"""
        return self._smart_split(text, self.child_chunk_size)

    def _smart_split(self, text: str, chunk_size: int) -> list[str]:
        """智能切块 - 在自然断点处分割"""
        if len(text) <= chunk_size:
            return [text]

        chunks = []
        start = 0
        text_len = len(text)

        while start < text_len:
            if start + chunk_size >= text_len:
                chunks.append(text[start:].strip())
                break

            end = self._find_best_split_point(text, start, start + chunk_size)
            chunks.append(text[start:end].strip())
            start = end

        return [c for c in chunks if c]

    def _find_best_split_point(self, text: str, start: int, target_end: int) -> int:
        """在 target_end 附近寻找最佳分割点"""
        text_len = len(text)
        search_end = min(target_end + 50, text_len)
        search_start = max(target_end - 100, start)

        # 优先级1: 段落分隔
        for i in range(target_end, search_start, -1):
            if i < text_len - 1 and text[i:i+2] == '\n\n':
                return i + 2

        # 优先级2: 句子结束
        sentence_ends = '。！？.!?\n'
        for i in range(target_end, search_start, -1):
            if text[i] in sentence_ends:
                return i + 1

        # 优先级3: 分句
        clause_seps = '，；,;'
        for i in range(target_end, search_start, -1):
            if text[i] in clause_seps:
                return i + 1

        return min(target_end, text_len)


# ============================================================
# Redis Child Chunk Store
# ============================================================

class RedisChildChunkStore:
    """
    Redis 子切块存储

    使用 Redis Hash 存储子切块，Key 格式:
    - child_chunk:{child_id} -> 子切块内容 JSON
    - kb:{kb_id}:doc:{document_id}:children -> 文档的所有子切块 ID 集合
    """

    def __init__(self):
        self._redis_client = None

    @property
    def redis_client(self):
        """懒加载 Redis 客户端"""
        if self._redis_client is None:
            import redis
            self._redis_client = redis.Redis.from_url(
                settings.redis_url,
                decode_responses=True,
            )
            logger.info("Redis 客户端初始化完成: {}", settings.redis_url)
        return self._redis_client

    def store_child_chunks(self, child_chunks: list[ChildChunk]) -> int:
        """批量存储子切块到 Redis"""
        if not child_chunks:
            return 0

        pipe = self.redis_client.pipeline()

        for chunk in child_chunks:
            key = f"child_chunk:{chunk.id}"
            value = json.dumps({
                "id": chunk.id,
                "parent_id": chunk.parent_id,
                "document_id": chunk.document_id,
                "kb_id": chunk.kb_id,
                "content": chunk.content,
                "chunk_index": chunk.chunk_index,
                "metadata": chunk.metadata,
            }, ensure_ascii=False)
            pipe.setex(key, settings.REDIS_CHILD_CHUNK_TTL, value)

            doc_children_key = f"kb:{chunk.kb_id}:doc:{chunk.document_id}:children"
            pipe.sadd(doc_children_key, chunk.id)
            pipe.expire(doc_children_key, settings.REDIS_CHILD_CHUNK_TTL)

        pipe.execute()

        logger.info("子切块存储完成: count={}", len(child_chunks))
        return len(child_chunks)

    def get_child_chunk(self, chunk_id: str) -> Optional[ChildChunk]:
        """获取单个子切块"""
        key = f"child_chunk:{chunk_id}"
        value = self.redis_client.get(key)
        if value is None:
            return None

        data = json.loads(value)
        return ChildChunk(
            id=data["id"],
            parent_id=data["parent_id"],
            document_id=data["document_id"],
            kb_id=data["kb_id"],
            content=data["content"],
            chunk_index=data["chunk_index"],
            metadata=data.get("metadata", {}),
        )

    def get_child_chunks_by_ids(self, chunk_ids: list[str]) -> list[ChildChunk]:
        """批量获取子切块"""
        if not chunk_ids:
            return []

        pipe = self.redis_client.pipeline()
        for chunk_id in chunk_ids:
            pipe.get(f"child_chunk:{chunk_id}")

        values = pipe.execute()
        chunks = []
        for i, value in enumerate(values):
            if value:
                data = json.loads(value)
                chunks.append(ChildChunk(
                    id=data["id"],
                    parent_id=data["parent_id"],
                    document_id=data["document_id"],
                    kb_id=data["kb_id"],
                    content=data["content"],
                    chunk_index=data["chunk_index"],
                    metadata=data.get("metadata", {}),
                ))
        return chunks

    def get_child_chunks_by_parent(self, parent_id: str, kb_id: int) -> list[ChildChunk]:
        """获取指定父切块的所有子切块"""
        parent_index = parent_id.split("_")[-1]
        doc_id = parent_id.split("_")[1]

        doc_children_key = f"kb:{kb_id}:doc:{doc_id}:children"
        all_child_ids = self.redis_client.smembers(doc_children_key)

        prefix = f"doc_{doc_id}_child_{parent_index}_"
        relevant_ids = [cid for cid in all_child_ids if cid.startswith(prefix)]

        return self.get_child_chunks_by_ids(relevant_ids)

    def delete_by_document_id(self, kb_id: int, document_id: int) -> int:
        """删除文档的所有子切块"""
        doc_children_key = f"kb:{kb_id}:doc:{document_id}:children"
        child_ids = self.redis_client.smembers(doc_children_key)

        if not child_ids:
            return 0

        pipe = self.redis_client.pipeline()
        for child_id in child_ids:
            pipe.delete(f"child_chunk:{child_id}")
        pipe.delete(doc_children_key)
        pipe.execute()

        logger.info("子切块删除完成: kb_id={}, doc_id={}, count={}", kb_id, document_id, len(child_ids))
        return len(child_ids)


# ============================================================
# LangChain RAG Service (使用 Chroma 向量存储)
# ============================================================

class LangChainRAGService:
    """
    LangChain RAG 服务

    实现基于 LangChain 的检索增强生成:
    1. 向量检索: 从 Chroma 检索相关父切块
    2. 精确匹配: 从 Redis 获取子切块进行精确匹配
    3. 上下文组装: 将检索结果组装为 LLM 上下文
    4. 答案生成: 调用 LLM 生成最终答案
    """

    def __init__(self):
        self.chunker = ParentChildChunker()
        self.child_store = RedisChildChunkStore()
        self._vector_store = None
        self._embedder = None

    @property
    def vector_store(self):
        """懒加载向量存储 (Chroma)"""
        if self._vector_store is None:
            from app.services.vector_store import get_vector_store
            self._vector_store = get_vector_store()
        return self._vector_store

    @property
    def embedder(self):
        """懒加载 Embedding 模型"""
        if self._embedder is None:
            from app.services.embedder import get_embedder
            self._embedder = get_embedder()
        return self._embedder

    def index_document(
        self,
        document_id: int,
        kb_id: int,
        content: str,
        file_name: str,
        owner_id: int,
        visibility: str,
        org_id: int,
    ) -> dict:
        """
        索引文档到 Chroma 和 Redis

        1. 将文档切分为父切块和子切块
        2. 父切块生成向量存入 Chroma
        3. 子切块存入 Redis
        """
        # 1. 父子切块
        parent_chunks, child_chunks = self.chunker.chunk_document(
            document_id, kb_id, content
        )

        if not parent_chunks:
            return {"parent_chunks": 0, "child_chunks": 0}

        # 2. 存储子切块到 Redis
        self.child_store.store_child_chunks(child_chunks)

        # 3. 存储父切块到 Chroma（带向量）
        from app.services.chunker import Chunk
        chunks = [Chunk(index=i, content=pc.content) for i, pc in enumerate(parent_chunks)]

        self.vector_store.add_chunks_full(
            kb_id=kb_id,
            document_id=document_id,
            chunks=chunks,
            file_name=file_name,
            owner_id=owner_id,
            visibility=visibility,
            org_id=org_id if org_id else 0,
        )

        return {
            "parent_chunks": len(parent_chunks),
            "child_chunks": len(child_chunks),
        }

    def search(
        self,
        kb_id: int,
        query: str,
        top_k: int = None,
    ) -> list[RetrievalResult]:
        """
        检索相关文档片段

        1. 对 query 进行 embedding
        2. 从 Chroma 检索相关父切块
        3. 从 Redis 获取对应的子切块
        4. 返回最相关的结果
        """
        top_k = top_k or settings.RAG_TOP_K

        # 1. 对 query 进行 embedding
        query_embedding = self.embedder.embed([query])[0].tolist()

        # 2. 从 Chroma 检索相关父切块
        Chroma_results = self.vector_store.query(
            kb_id=kb_id,
            query_embedding=query_embedding,
            top_k=top_k * 2,
        )

        results = []

        if Chroma_results and Chroma_results.get("ids"):
            ids = Chroma_results["ids"][0]
            documents = Chroma_results.get("documents", [[]])[0]
            distances = Chroma_results.get("distances", [[]])[0]
            metadatas = Chroma_results.get("metadatas", [[]])[0]

            for i, chunk_id in enumerate(ids):
                # Chroma score 已在 vector_store 中转换为距离格式
                distance = distances[i] if i < len(distances) else 1.0
                # Chroma COSINE 返回相似度，转换为 score
                score = 1.0 - distance

                if score < settings.RAG_SIMILARITY_THRESHOLD:
                    continue

                metadata = metadatas[i] if i < len(metadatas) else {}
                document_id = metadata.get("document_id", 0)

                # 3. 尝试从 Redis 获取更精确的子切块
                child_chunks = self.child_store.get_child_chunks_by_ids(
                    [f"doc_{document_id}_child_{i}_{j}" for j in range(3)]
                )

                if child_chunks:
                    for child in child_chunks[:2]:
                        results.append(RetrievalResult(
                            content=child.content,
                            score=score,
                            source="child",
                            document_id=document_id,
                            chunk_index=child.chunk_index,
                            metadata={
                                "parent_chunk_id": chunk_id,
                                "child_chunk_id": child.id,
                                "kb_id": kb_id,
                                **metadata,
                            },
                        ))
                else:
                    results.append(RetrievalResult(
                        content=documents[i] if i < len(documents) else "",
                        score=score,
                        source="parent",
                        document_id=document_id,
                        chunk_index=i,
                        metadata=metadata,
                    ))

        results.sort(key=lambda x: x.score, reverse=True)

        return results[:top_k]

    def delete_document(self, kb_id: int, document_id: int) -> dict:
        """删除文档的索引数据"""
        deleted_vectors = self.vector_store.delete_by_document_id(kb_id, document_id)
        deleted_chunks = self.child_store.delete_by_document_id(kb_id, document_id)

        return {
            "deleted_vectors": deleted_vectors,
            "deleted_child_chunks": deleted_chunks,
        }


# ============================================================
# Module-level convenience
# ============================================================

_rag_service: Optional[LangChainRAGService] = None


def get_rag_service() -> LangChainRAGService:
    """Return cached LangChainRAGService singleton."""
    global _rag_service
    if _rag_service is None:
        _rag_service = LangChainRAGService()
    return _rag_service
