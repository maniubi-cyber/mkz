"""
LangChain RAG Service - 基于 LangChain 的检索增强生成服务

核心设计（父子切块策略，检索入口在子块）:
- 文档元数据: MySQL
- 子切块 (Child Chunks, 句子级 ~200 字符): Qdrant 向量数据库（embedding 检索入口）
- 父切块 (Parent Chunks, 段落级 ~1000 字符): Redis 缓存（命中后回溯提供完整上下文）
- LLM 自动提取元数据 → Qdrant payload（支持元数据权限过滤 + 语义筛选）

父子切块策略:
1. 文档被切成较大的父切块（保留段落语义），存入 Redis
2. 每个父切块再细分为句子级子切块，embedding 后存入 Qdrant 作为检索入口
3. 检索时先在 Qdrant 命中子块，再通过 parent_id 回溯 Redis 中的父块，提供完整上下文
4. 文档编辑时通过 doc_version 定位旧版本子块增量重建，避免全量重构的 token 消耗
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
    """子切块 - embedding 存入 Qdrant"""
    id: str
    parent_id: str  # 所属父切块 ID
    parent_index: int  # 所属父切块在文档内的序号
    document_id: int
    kb_id: int
    content: str
    index: int  # 子块在父块内的序号
    doc_version: int = 1
    metadata: dict = field(default_factory=dict)


@dataclass
class ParentChunk:
    """父切块 - 存入 Redis（命中后回溯）"""
    id: str
    document_id: int
    kb_id: int
    content: str
    index: int  # 父块在文档内的序号
    doc_version: int = 1
    child_chunk_ids: list[str] = field(default_factory=list)
    metadata: dict = field(default_factory=dict)


@dataclass
class RetrievalResult:
    """检索结果（已回溯父块，content 为父块完整上下文）"""
    content: str
    score: float
    source: str  # parent（回溯后的父块内容）
    document_id: int
    chunk_index: int
    metadata: dict = field(default_factory=dict)


# ============================================================
# Parent-Child Chunker
# ============================================================

class ParentChildChunker:
    """
    父子切块器

    1. 父切块: 段落级 (~1000 字符)，保留完整段落语义，存入 Redis
    2. 子切块: 句子级 (~200 字符)，作为检索入口，embedding 存入 Qdrant
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
        self,
        document_id: int,
        kb_id: int,
        content: str,
        doc_version: int = 1,
    ) -> tuple[list[ParentChunk], list[ChildChunk]]:
        """将文档内容切分为父切块和子切块。"""
        if not content or not content.strip():
            return [], []

        parent_texts = self._split_into_parent_chunks(content)

        parent_chunks = []
        all_child_chunks = []

        for i, parent_text in enumerate(parent_texts):
            parent_id = f"doc_{document_id}_parent_{i}"

            child_texts = self._split_into_child_chunks(parent_text)

            child_chunks = []
            for j, child_text in enumerate(child_texts):
                child_id = f"doc_{document_id}_child_{i}_{j}"
                child_chunk = ChildChunk(
                    id=child_id,
                    parent_id=parent_id,
                    parent_index=i,
                    document_id=document_id,
                    kb_id=kb_id,
                    content=child_text,
                    index=j,
                    doc_version=doc_version,
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
                index=i,
                doc_version=doc_version,
                child_chunk_ids=[c.id for c in child_chunks],
                metadata={
                    "parent_index": i,
                    "child_count": len(child_chunks),
                }
            )
            parent_chunks.append(parent_chunk)
            all_child_chunks.extend(child_chunks)

        logger.info(
            "父子切块完成: doc_id={}, kb_id={}, version={}, 父块={}, 子块={}",
            document_id, kb_id, doc_version, len(parent_chunks), len(all_child_chunks),
        )

        return parent_chunks, all_child_chunks

    def _split_into_parent_chunks(self, text: str) -> list[str]:
        """父切块: 段落级分割（保留段落语义）。"""
        return self._smart_split(text, self.parent_chunk_size)

    def _split_into_child_chunks(self, text: str) -> list[str]:
        """子切块: 句子级分割（切至句子级）。"""
        return self._smart_split(text, self.child_chunk_size, sentence_level=True)

    def _smart_split(
        self, text: str, chunk_size: int, sentence_level: bool = False,
    ) -> list[str]:
        """智能切块 - 在自然断点处分割。"""
        if len(text) <= chunk_size:
            return [text]

        chunks = []
        start = 0
        text_len = len(text)

        while start < text_len:
            if start + chunk_size >= text_len:
                chunks.append(text[start:].strip())
                break

            end = self._find_best_split_point(text, start, start + chunk_size, sentence_level)
            chunks.append(text[start:end].strip())
            start = end

        return [c for c in chunks if c]

    def _find_best_split_point(
        self, text: str, start: int, target_end: int, sentence_level: bool = False,
    ) -> int:
        """在 target_end 附近寻找最佳分割点。子块优先在句末切。"""
        text_len = len(text)
        search_end = min(target_end + 50, text_len)
        search_start = max(target_end - 100, start)

        # 优先级1: 段落分隔（父块优先）
        if not sentence_level:
            for i in range(target_end, search_start, -1):
                if i < text_len - 1 and text[i:i + 2] == '\n\n':
                    return i + 2

        # 优先级2: 句子结束（子块优先）
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
# Redis Parent Chunk Store（父块回溯）
# ============================================================

class RedisParentChunkStore:
    """
    Redis 父切块存储（命中子块后回溯父块提供完整上下文）。

    Key 格式:
    - parent_chunk:{parent_id} -> 父块内容 JSON
    - kb:{kb_id}:doc:{document_id}:parents -> 文档的所有父块 ID 集合
    - doc:{document_id}:version -> 文档当前版本号
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

    def store_parent_chunks(self, parent_chunks: list[ParentChunk]) -> int:
        """批量存储父切块到 Redis（用于命中后回溯）。"""
        if not parent_chunks:
            return 0

        pipe = self.redis_client.pipeline()

        for chunk in parent_chunks:
            key = f"parent_chunk:{chunk.id}"
            value = json.dumps({
                "id": chunk.id,
                "document_id": chunk.document_id,
                "kb_id": chunk.kb_id,
                "content": chunk.content,
                "index": chunk.index,
                "doc_version": chunk.doc_version,
                "child_chunk_ids": chunk.child_chunk_ids,
                "metadata": chunk.metadata,
            }, ensure_ascii=False)
            pipe.setex(key, settings.REDIS_CHILD_CHUNK_TTL, value)

            doc_parents_key = f"kb:{chunk.kb_id}:doc:{chunk.document_id}:parents"
            pipe.sadd(doc_parents_key, chunk.id)
            pipe.expire(doc_parents_key, settings.REDIS_CHILD_CHUNK_TTL)

        # 记录文档当前版本号
        if parent_chunks:
            doc_id = parent_chunks[0].document_id
            kb_id = parent_chunks[0].kb_id
            version = parent_chunks[0].doc_version
            version_key = f"doc:{doc_id}:version"
            pipe.set(version_key, version, ex=settings.REDIS_CHILD_CHUNK_TTL)

        pipe.execute()

        logger.info("父块存储完成: count={}", len(parent_chunks))
        return len(parent_chunks)

    def get_parent_chunk(self, parent_id: str) -> Optional[ParentChunk]:
        """获取单个父切块（回溯用）。"""
        key = f"parent_chunk:{parent_id}"
        value = self.redis_client.get(key)
        if value is None:
            return None

        data = json.loads(value)
        return ParentChunk(
            id=data["id"],
            document_id=data["document_id"],
            kb_id=data["kb_id"],
            content=data["content"],
            index=data["index"],
            doc_version=data.get("doc_version", 1),
            child_chunk_ids=data.get("child_chunk_ids", []),
            metadata=data.get("metadata", {}),
        )

    def get_parent_chunks_by_ids(self, parent_ids: list[str]) -> list[ParentChunk]:
        """批量获取父切块（去重后批量回溯）。"""
        if not parent_ids:
            return []

        # 去重，保持顺序
        seen = set()
        unique_ids = []
        for pid in parent_ids:
            if pid and pid not in seen:
                seen.add(pid)
                unique_ids.append(pid)

        pipe = self.redis_client.pipeline()
        for pid in unique_ids:
            pipe.get(f"parent_chunk:{pid}")

        values = pipe.execute()
        chunks = []
        for value in values:
            if value:
                data = json.loads(value)
                chunks.append(ParentChunk(
                    id=data["id"],
                    document_id=data["document_id"],
                    kb_id=data["kb_id"],
                    content=data["content"],
                    index=data["index"],
                    doc_version=data.get("doc_version", 1),
                    child_chunk_ids=data.get("child_chunk_ids", []),
                    metadata=data.get("metadata", {}),
                ))
        return chunks

    def get_doc_version(self, document_id: int) -> int:
        """获取文档当前版本号（增量重建定位用）。"""
        version_key = f"doc:{document_id}:version"
        val = self.redis_client.get(version_key)
        return int(val) if val else 0

    def delete_by_document_id(self, kb_id: int, document_id: int) -> int:
        """删除文档的所有父块。"""
        doc_parents_key = f"kb:{kb_id}:doc:{document_id}:parents"
        parent_ids = self.redis_client.smembers(doc_parents_key)

        if not parent_ids:
            return 0

        pipe = self.redis_client.pipeline()
        for pid in parent_ids:
            pipe.delete(f"parent_chunk:{pid}")
        pipe.delete(doc_parents_key)
        pipe.delete(f"doc:{document_id}:version")
        pipe.execute()

        logger.info("父块删除完成: kb_id={}, doc_id={}, count={}", kb_id, document_id, len(parent_ids))
        return len(parent_ids)


# ============================================================
# LangChain RAG Service（子块入 Qdrant / 父块回溯 / 版本号增量重建）
# ============================================================

class LangChainRAGService:
    """
    LangChain RAG 服务

    1. 索引: 父子切块 → 父块入 Redis / 子块 embedding 入 Qdrant
    2. 检索: Qdrant 命中子块 → parent_id 回溯 Redis 父块 → 完整上下文
    3. 增量重建: 编辑时按 doc_version 删除旧子块，仅重建变更版本
    """

    def __init__(self):
        self.chunker = ParentChildChunker()
        self.parent_store = RedisParentChunkStore()
        self._vector_store = None
        self._embedder = None
        self._metadata_extractor = None
        self._es_bm25 = None

    @property
    def es_bm25(self):
        """懒加载 ES BM25 客户端（写入/删除/检索双路中的关键词路）。"""
        if self._es_bm25 is None:
            from app.services.es_bm25 import get_es_bm25_client
            self._es_bm25 = get_es_bm25_client()
        return self._es_bm25

    @property
    def vector_store(self):
        """懒加载向量存储 (Qdrant)"""
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

    @property
    def metadata_extractor(self):
        """懒加载 LLM 元数据提取器"""
        if self._metadata_extractor is None:
            from app.services.metadata_extractor import get_metadata_extractor
            self._metadata_extractor = get_metadata_extractor()
        return self._metadata_extractor

    def index_document(
        self,
        document_id: int,
        kb_id: int,
        content: str,
        file_name: str,
        owner_id: int,
        visibility: str,
        org_id: int,
        doc_version: int = 1,
    ) -> dict:
        """
        索引文档（全量）:
        1. 父子切块（父块段落级 / 子块句子级）
        2. LLM 自动提取父块元数据（topic / keywords）
        3. 父块存入 Redis（回溯用）
        4. 子块 embedding 存入 Qdrant（检索入口，payload 带元数据 + 版本号）
        """
        # 1. 父子切块
        parent_chunks, child_chunks = self.chunker.chunk_document(
            document_id, kb_id, content, doc_version=doc_version
        )

        if not parent_chunks:
            return {"parent_chunks": 0, "child_chunks": 0}

        # 2. LLM 提取父块元数据
        metadata_list = self.metadata_extractor.extract_batch(parent_chunks)
        # 子块继承所属父块的元数据
        child_metadata_list = []
        for child in child_chunks:
            parent_meta = (
                metadata_list[child.parent_index]
                if child.parent_index < len(metadata_list)
                else {"topic": "", "keywords": ""}
            )
            child_metadata_list.append(parent_meta)

        # 3. 父块存入 Redis（回溯用）
        self.parent_store.store_parent_chunks(parent_chunks)

        # 4. 子块 embedding 存入 Qdrant（检索入口）
        self.vector_store.add_child_chunks(
            kb_id=kb_id,
            document_id=document_id,
            child_chunks=child_chunks,
            file_name=file_name,
            owner_id=owner_id,
            visibility=visibility,
            org_id=org_id if org_id else 0,
            doc_version=doc_version,
            metadata_list=child_metadata_list,
        )

        # 5. 子块同步写入 ES BM25 索引（双路召回关键词路）
        #    ES 不可用 / ES_BM25_ENABLED=False 时静默降级为 0，不阻塞主流程。
        es_indexed = self.es_bm25.index_child_chunks(
            kb_id=kb_id,
            document_id=document_id,
            child_chunks=child_chunks,
            file_name=file_name,
            owner_id=owner_id,
            visibility=visibility,
            org_id=org_id if org_id else 0,
            doc_version=doc_version,
            metadata_list=child_metadata_list,
        )

        return {
            "parent_chunks": len(parent_chunks),
            "child_chunks": len(child_chunks),
            "es_bm25_indexed": es_indexed,
        }

    def rebuild_document_incremental(
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
        增量重建文档（编辑场景）:
        1. 读取 Redis 中文档当前版本号 old_version
        2. 仅删除 Qdrant 中 old_version 的子块（不触碰其他版本/文档）
        3. 以 new_version = old_version + 1 重新切块入库
        缓解频繁全量重构的 token 消耗
        """
        old_version = self.parent_store.get_doc_version(document_id)
        new_version = old_version + 1

        # 1. 删除旧版本子块（按 doc_version 过滤，不影响并发其他版本写入）
        deleted = 0
        es_deleted = 0
        if old_version > 0:
            deleted = self.vector_store.delete_by_version(
                kb_id=kb_id, document_id=document_id, old_version=old_version
            )
            es_deleted = self.es_bm25.delete_by_version(
                kb_id=kb_id, document_id=document_id, old_version=old_version
            )

        # 2. 以新版本重新索引
        result = self.index_document(
            document_id=document_id,
            kb_id=kb_id,
            content=content,
            file_name=file_name,
            owner_id=owner_id,
            visibility=visibility,
            org_id=org_id,
            doc_version=new_version,
        )

        result["old_version"] = old_version
        result["new_version"] = new_version
        result["deleted_old_child_chunks"] = deleted
        result["deleted_old_es_chunks"] = es_deleted
        return result

    def search(
        self,
        kb_id: int,
        query: str,
        top_k: int = None,
        user_id: int = 0,
        role: str = "USER",
        org_id: int = 0,
    ) -> list[RetrievalResult]:
        """
        检索（子块命中 → 回溯父块）:
        1. query embedding
        2. Qdrant 检索子块（含元数据权限过滤）
        3. 通过 parent_id 回溯 Redis 中的父块，提供完整上下文
        4. 返回父块内容作为 LLM 上下文
        """
        top_k = top_k or settings.RAG_TOP_K

        # 1. query embedding
        query_embedding = self.embedder.embed_query(query).tolist()

        # 2. Qdrant 检索子块（权限过滤在 Qdrant payload 层完成）
        child_results = self.vector_store.query(
            kb_id=kb_id,
            query_embedding=query_embedding,
            top_k=top_k * 2,
            user_id=user_id,
            role=role,
            org_id=org_id,
        )

        if not child_results:
            return []

        # 3. 回溯父块（批量去重）
        parent_ids = [r["metadata"].get("parent_id", "") for r in child_results]
        parent_map: dict[str, ParentChunk] = {
            p.id: p for p in self.parent_store.get_parent_chunks_by_ids(parent_ids)
        }

        # 4. 组装结果（用父块内容作为完整上下文）
        results: list[RetrievalResult] = []
        for child in child_results:
            meta = child.get("metadata", {})
            parent_id = meta.get("parent_id", "")
            parent = parent_map.get(parent_id)

            # 父块存在则用父块完整上下文，否则退化用子块内容
            content = parent.content if parent else child.get("content", "")
            results.append(RetrievalResult(
                content=content,
                score=child.get("score", 0.0),
                source="parent",
                document_id=int(meta.get("document_id", 0)),
                chunk_index=int(meta.get("parent_index", 0)),
                metadata={
                    **meta,
                    "parent_chunk_id": parent_id,
                    "child_chunk_id": child.get("id", ""),
                    "kb_id": kb_id,
                    "file_name": meta.get("file_name", ""),
                },
            ))

        results.sort(key=lambda x: x.score, reverse=True)
        return results[:top_k]

    def delete_document(self, kb_id: int, document_id: int) -> dict:
        """删除文档的索引数据（Qdrant 子块 + Redis 父块 + ES BM25 子块）。"""
        deleted_vectors = self.vector_store.delete_by_document_id(kb_id, document_id)
        deleted_parents = self.parent_store.delete_by_document_id(kb_id, document_id)
        deleted_es = self.es_bm25.delete_by_document_id(kb_id, document_id)

        return {
            "deleted_child_vectors": deleted_vectors,
            "deleted_parent_chunks": deleted_parents,
            "deleted_es_chunks": deleted_es,
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
