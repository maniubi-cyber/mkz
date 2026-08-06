"""
Qdrant Vector Store Service

使用 Qdrant 作为向量数据库，存储**子切块**的 embedding 作为检索入口。
命中后通过 parent_id 回溯到父切块（存于 Redis）以提供完整上下文。

父子切块策略:
- 子切块 (句子级, ~200 字符) → embedding → Qdrant（检索入口，高召回）
- 父切块 (段落级, ~1000 字符) → Redis（回溯提供完整上下文）
- LLM 自动提取元数据 → Qdrant payload（支持元数据权限过滤）

Qdrant 优势:
- 生产级向量数据库，独立服务部署，支持水平扩展
- HNSW 索引 + payload 过滤，支持元数据权限过滤
- gRPC/REST 双协议，gRPC 吞吐更高

Collection 命名: kb_{kb_id}
距离度量: Cosine（默认，适合归一化向量）

每个子切块的 payload:
    document_id    (int)    — 源文档主键
    kb_id          (int)    — 知识库主键
    parent_id      (str)    — 所属父切块 ID（回溯父块用）
    parent_index   (int)    — 父切块在文档内的序号
    chunk_index    (int)    — 子切块在父块内的序号
    file_name      (str)    — 原始文件名
    owner_id       (int)    — 上传者用户 ID
    visibility     (str)    — PRIVATE | PUBLIC | ORG
    org_id         (int)    — 组织 ID（0 表示不适用）
    doc_version    (int)    — 文档版本号（用于增量重建定位）
    topic          (str)    — LLM 提取的主题
    keywords       (str)    — LLM 提取的关键词（逗号分隔）
"""

from __future__ import annotations

import logging
import uuid
from typing import Any, Optional

from app.core.config import settings

logger = logging.getLogger(__name__)


class VectorStore:
    """
    Qdrant 向量存储封装类。

    Usage::

        store = VectorStore.get_instance()
        store.add_child_chunks(kb_id=1, document_id=42, chunks=child_chunks, ...)
        store.delete_by_document_id(kb_id=1, doc_id=42)
        store.query(kb_id=1, query_embedding=vec, top_k=5, filter=...)
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

    # ---- Internal: Collection Name ----

    def _get_collection_name(self, kb_id: int) -> str:
        """获取 collection 名称。"""
        return f"{self._collection_prefix}{kb_id}"

    # ---- Internal: Client Lazy-Load ----

    def _get_client(self):
        """获取 Qdrant 客户端（懒加载，优先 gRPC）。"""
        if self._client is None:
            from qdrant_client import QdrantClient

            kwargs: dict[str, Any] = {
                "host": settings.QDRANT_HOST,
                "port": settings.QDRANT_PORT,
                "grpc_port": settings.QDRANT_GRPC_PORT,
                "prefer_grpc": settings.QDRANT_PREFER_GRPC,
            }
            if settings.QDRANT_API_KEY:
                kwargs["api_key"] = settings.QDRANT_API_KEY

            self._client = QdrantClient(**kwargs)
            logger.info(
                "Qdrant 客户端初始化完成: host=%s, port=%d, grpc=%s",
                settings.QDRANT_HOST, settings.QDRANT_PORT,
                settings.QDRANT_PREFER_GRPC,
            )
        return self._client

    # ---- Internal: Distance Mapping ----

    def _distance_str(self) -> str:
        """Map config distance to Qdrant Distance enum string."""
        from qdrant_client.models import Distance
        mapping = {
            "cosine": Distance.COSINE,
            "euclid": Distance.EUCLID,
            "dot": Distance.DOT,
        }
        return mapping.get(settings.QDRANT_DISTANCE.lower(), Distance.COSINE)

    # ---- Collection Management ----

    def _ensure_collection_exists(self, collection_name: str):
        """确保 collection 存在，不存在则创建（含 HNSW + payload 索引）。"""
        from qdrant_client.models import (
            VectorParams,
            HnswConfigDiff,
            PayloadSchemaType,
        )

        client = self._get_client()
        collections = {c.name for c in client.get_collections().collections}
        if collection_name in collections:
            return

        client.create_collection(
            collection_name=collection_name,
            vectors_config=VectorParams(
                size=self._dimension,
                distance=self._distance_str(),
            ),
            hnsw_config=HnswConfigDiff(
                m=16,
                ef_construct=100,
                full_scan_threshold=10000,
            ),
        )

        # 为权限过滤字段建立 payload 索引（加速过滤）
        for field, ftype in [
            ("document_id", PayloadSchemaType.INTEGER),
            ("owner_id", PayloadSchemaType.INTEGER),
            ("visibility", PayloadSchemaType.KEYWORD),
            ("org_id", PayloadSchemaType.INTEGER),
            ("parent_id", PayloadSchemaType.KEYWORD),
            ("doc_version", PayloadSchemaType.INTEGER),
        ]:
            try:
                client.create_payload_index(
                    collection_name=collection_name,
                    field_name=field,
                    field_schema=ftype,
                )
            except Exception as e:
                logger.debug("payload index 创建跳过 %s: %s", field, e)

        logger.info("Qdrant collection 创建: %s", collection_name)

    # ---- Public API: 写入子切块 ----

    def add_child_chunks(
        self,
        kb_id: int,
        document_id: int,
        child_chunks: list[Any],
        file_name: str,
        owner_id: int,
        visibility: str,
        org_id: int,
        doc_version: int = 1,
        metadata_list: Optional[list[dict]] = None,
    ) -> int:
        """
        将子切块向量化后插入到 Qdrant collection 中。

        Args:
            kb_id:          知识库 ID
            document_id:    文档主键
            child_chunks:   子切块对象列表（需含 content / parent_id / parent_index / index）
            file_name:      原始文件名
            owner_id:       上传者用户 ID
            visibility:     权限范围 (PRIVATE/PUBLIC/ORG)
            org_id:         组织 ID
            doc_version:    文档版本号（用于增量重建定位）
            metadata_list:  LLM 提取的元数据列表（topic/keywords），与 child_chunks 等长

        Returns:
            插入的子切块数量
        """
        if not child_chunks:
            return 0

        collection_name = self._get_collection_name(kb_id)
        self._ensure_collection_exists(collection_name)

        from app.services.embedder import get_embedder
        from qdrant_client.models import PointStruct

        embedder = get_embedder()

        # 批量 embedding
        texts = [c.content for c in child_chunks]
        vectors = [vec.tolist() for vec in embedder.embed(texts)]

        points: list[PointStruct] = []
        for i, chunk in enumerate(child_chunks):
            point_id = self._make_point_id(document_id, chunk)
            extra_meta = metadata_list[i] if metadata_list and i < len(metadata_list) else {}

            payload = {
                "content": chunk.content,
                "document_id": document_id,
                "kb_id": kb_id,
                "parent_id": chunk.parent_id,
                "parent_index": chunk.parent_index,
                "chunk_index": chunk.index,
                "file_name": file_name,
                "owner_id": owner_id,
                "visibility": visibility,
                "org_id": org_id if org_id else 0,
                "doc_version": doc_version,
                "topic": extra_meta.get("topic", ""),
                "keywords": extra_meta.get("keywords", ""),
            }
            points.append(PointStruct(id=point_id, vector=vectors[i], payload=payload))

        # 分批 upsert（Qdrant 单次建议 <= 256）
        client = self._get_client()
        batch_size = 256
        for start in range(0, len(points), batch_size):
            client.upsert(
                collection_name=collection_name,
                points=points[start:start + batch_size],
                wait=True,
            )

        logger.info(
            "Qdrant 子块写入完成: kb_id={}, doc_id={}, version={}, count={}",
            kb_id, document_id, doc_version, len(child_chunks),
        )
        return len(child_chunks)

    def _make_point_id(self, document_id: int, chunk: Any) -> str:
        """生成稳定的子切块 point ID（基于文档ID+父块序号+子块序号）。

        使用确定性 UUIDv5：Qdrant gRPC 要求 UUID 格式的字符串 ID，
        且确定性保证重复写入幂等覆盖（upsert 同 ID 更新）。
        """
        raw = f"doc_{document_id}_p{chunk.parent_index}_c{chunk.index}"
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, raw))

    # ---- Public API: 删除 ----

    def delete_by_document_id(self, kb_id: int, document_id: int) -> int:
        """
        删除指定文档的所有子块向量。

        Returns:
            删除的记录数（Qdrant 不直接返回数量，这里通过 filter 删除并估算）。
        """
        from qdrant_client.models import Filter, FieldCondition, MatchValue

        collection_name = self._get_collection_name(kb_id)
        self._ensure_collection_exists(collection_name)

        client = self._get_client()
        # 先统计数量
        try:
            count_resp = client.count(
                collection_name=collection_name,
                count_filter=Filter(must=[
                    FieldCondition(key="document_id", match=MatchValue(value=document_id))
                ]),
                exact=True,
            )
            deleted = count_resp.count
        except Exception:
            deleted = -1

        client.delete(
            collection_name=collection_name,
            points_selector=Filter(must=[
                FieldCondition(key="document_id", match=MatchValue(value=document_id))
            ]),
            wait=True,
        )

        logger.info(
            "Qdrant 删除完成: kb_id={}, doc_id={}, deleted={}",
            kb_id, document_id, deleted,
        )
        return max(deleted, 0)

    def delete_by_version(self, kb_id: int, document_id: int, old_version: int) -> int:
        """
        按版本号删除（增量重建场景：删除旧版本的子块）。
        """
        from qdrant_client.models import Filter, FieldCondition, MatchValue

        collection_name = self._get_collection_name(kb_id)
        self._ensure_collection_exists(collection_name)

        client = self._get_client()
        delete_filter = Filter(must=[
            FieldCondition(key="document_id", match=MatchValue(value=document_id)),
            FieldCondition(key="doc_version", match=MatchValue(value=old_version)),
        ])

        try:
            count_resp = client.count(
                collection_name=collection_name,
                count_filter=delete_filter,
                exact=True,
            )
            deleted = count_resp.count
        except Exception:
            deleted = -1

        client.delete(
            collection_name=collection_name,
            points_selector=delete_filter,
            wait=True,
        )

        logger.info(
            "Qdrant 版本删除完成: kb_id={}, doc_id={}, old_version={}, deleted={}",
            kb_id, document_id, old_version, deleted,
        )
        return max(deleted, 0)

    # ---- Public API: 统计 ----

    def count(self, kb_id: int) -> int:
        """返回 collection 中的向量数量。"""
        collection_name = self._get_collection_name(kb_id)
        try:
            client = self._get_client()
            self._ensure_collection_exists(collection_name)
            return client.count(collection_name=collection_name, exact=True).count
        except Exception:
            return 0

    # ---- Public API: 向量检索 ----

    def query(
        self,
        kb_id: int,
        query_embedding: list[float],
        top_k: int = 5,
        user_id: int = 0,
        role: str = "USER",
        org_id: int = 0,
    ) -> list[dict[str, Any]]:
        """
        向量相似度搜索（含元数据权限过滤）。

        权限规则（在 Qdrant payload 过滤层完成）:
        - ADMIN → 不过滤
        - owner_id == user_id → 命中
        - visibility == PUBLIC → 命中
        - visibility == ORG AND org_id == user's org_id → 命中

        Args:
            kb_id:           知识库 ID
            query_embedding: 查询向量
            top_k:           返回结果数量
            user_id:         当前用户 ID（0 = 匿名）
            role:            USER / ADMIN
            org_id:          用户组织 ID

        Returns:
            统一格式结果列表: [{id, content, score, metadata}, ...]
        """
        from qdrant_client.models import (
            Filter, FieldCondition, MatchValue, MatchAny, Range,
        )

        collection_name = self._get_collection_name(kb_id)
        self._ensure_collection_exists(collection_name)
        client = self._get_client()

        # 构建权限过滤条件
        query_filter = self._build_permission_filter(user_id, role, org_id)

        results = client.search(
            collection_name=collection_name,
            query_vector=query_embedding,
            limit=top_k,
            query_filter=query_filter,
            with_payload=True,
        )

        formatted: list[dict[str, Any]] = []
        for hit in results:
            payload = hit.payload or {}
            formatted.append({
                "id": str(hit.id),
                "content": payload.get("content", ""),
                "score": float(hit.score),
                "metadata": payload,
            })

        return formatted

    def _build_permission_filter(self, user_id: int, role: str, org_id: int):
        """
        构建 Qdrant 权限过滤 Filter。

        逻辑（should = OR，满足任一即命中）:
        - ADMIN → 返回 None（不过滤）
        - owner_id == user_id  OR  visibility == PUBLIC
          OR  (visibility == ORG AND org_id == user_org_id)
        """
        from qdrant_client.models import Filter, FieldCondition, MatchValue

        if role and role.upper() == "ADMIN":
            return None

        should: list[Filter] = []

        # owner 命中
        if user_id != 0:
            should.append(Filter(must=[
                FieldCondition(key="owner_id", match=MatchValue(value=user_id))
            ]))
        # PUBLIC
        should.append(Filter(must=[
            FieldCondition(key="visibility", match=MatchValue(value="PUBLIC"))
        ]))
        # ORG 同组织
        if org_id != 0:
            should.append(Filter(must=[
                FieldCondition(key="visibility", match=MatchValue(value="ORG")),
                FieldCondition(key="org_id", match=MatchValue(value=org_id)),
            ]))

        return Filter(should=should)

    def delete_collection(self, kb_id: int) -> bool:
        """删除指定知识库的整个 collection。"""
        client = self._get_client()
        collection_name = self._get_collection_name(kb_id)
        try:
            client.delete_collection(collection_name=collection_name)
            logger.info("Qdrant collection 已删除: {}", collection_name)
            return True
        except Exception as e:
            logger.warning("Qdrant collection 删除失败: {}, error={}",
                           collection_name, e)
            return False


# ============================================================
# Module-level convenience
# ============================================================

def get_vector_store() -> VectorStore:
    """返回单例 VectorStore 实例。"""
    return VectorStore.get_instance()
