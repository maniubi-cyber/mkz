"""
Elasticsearch BM25 Indexer & Searcher

将子切块写入 ES（IK 中文分词），通过 ES 内置 BM25 检索，
与 Qdrant 向量检索形成双路召回，再经 RRF 融合。

索引映射:
- 每个知识库一个索引: kb_{kb_id}
- content 字段: ik_max_word（索引时最细粒度）/ ik_smart（查询时智能）
- payload: document_id / parent_id / owner_id / visibility / org_id（用于权限过滤）

降级策略:
- ES 不可用或 ES_BM25_ENABLED=False 时，回退到本地 rank-bm25（BM25IndexManager）
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from app.core.config import settings

logger = logging.getLogger(__name__)


class ESBM25Client:
    """
    Elasticsearch BM25 客户端。

    Usage::

        client = ESBM25Client.get_instance()
        client.index_child_chunks(kb_id=1, chunks=child_chunks, ...)
        results = client.search(kb_id=1, query="知识库", top_k=10,
                                  user_id=10, role="USER", org_id=5)
    """

    _instance: Optional["ESBM25Client"] = None

    def __init__(self) -> None:
        self._client = None
        self._available: Optional[bool] = None

    @classmethod
    def get_instance(cls) -> "ESBM25Client":
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    # ---- Client Lazy-Load ----

    def _get_client(self):
        if self._client is None:
            try:
                from elasticsearch import Elasticsearch
                kwargs: dict[str, Any] = {"hosts": [settings.ES_URIS]}
                if settings.ES_USERNAME:
                    kwargs["basic_auth"] = (settings.ES_USERNAME, settings.ES_PASSWORD)
                self._client = Elasticsearch(**kwargs)
                logger.info("ES BM25 客户端初始化完成: %s", settings.ES_URIS)
            except Exception as e:
                logger.warning("ES 客户端初始化失败，将回退本地 BM25: %s", e)
                self._client = None
        return self._client

    @property
    def available(self) -> bool:
        """ES 是否可用（懒探测，结果缓存）。"""
        if self._available is not None:
            return self._available
        if not settings.ES_BM25_ENABLED:
            self._available = False
            return False
        client = self._get_client()
        if client is None:
            self._available = False
            return False
        try:
            self._available = bool(client.ping())
        except Exception:
            self._available = False
        if not self._available:
            logger.warning("ES 不可达，BM25 将回退到本地 rank-bm25")
        return self._available

    # ---- Index Name ----

    def _index_name(self, kb_id: int) -> str:
        return f"{settings.ES_BM25_INDEX_PREFIX}{kb_id}"

    def _index_analyzer(self) -> str:
        return "ik_max_word" if settings.ES_ANALYZER_IK else "standard"

    def _search_analyzer(self) -> str:
        return "ik_smart" if settings.ES_ANALYZER_IK else "standard"

    # ---- Index Management ----

    def _ensure_index(self, kb_id: int) -> None:
        """确保索引存在，含 IK/standard 分词映射。"""
        client = self._get_client()
        if client is None:
            return
        index_name = self._index_name(kb_id)
        try:
            if client.indices.exists(index=index_name):
                return
            mapping = {
                "mappings": {
                    "properties": {
                        "content": {
                            "type": "text",
                            "analyzer": self._index_analyzer(),
                            "search_analyzer": self._search_analyzer(),
                        },
                        "document_id": {"type": "integer"},
                        "kb_id": {"type": "integer"},
                        "parent_id": {"type": "keyword"},
                        "parent_index": {"type": "integer"},
                        "chunk_index": {"type": "integer"},
                        "file_name": {
                            "type": "text",
                            "analyzer": self._index_analyzer(),
                            "search_analyzer": self._search_analyzer(),
                        },
                        "owner_id": {"type": "integer"},
                        "visibility": {"type": "keyword"},
                        "org_id": {"type": "integer"},
                        "doc_version": {"type": "integer"},
                        "topic": {
                            "type": "text",
                            "analyzer": self._index_analyzer(),
                            "search_analyzer": self._search_analyzer(),
                        },
                        "keywords": {"type": "keyword"},
                    }
                }
            }
            client.indices.create(index=index_name, body=mapping)
            logger.info("ES BM25 索引创建: %s (analyzer=%s)", index_name, self._index_analyzer())
        except Exception as e:
            logger.warning("ES 索引创建失败 %s: %s", index_name, e)

    # ---- Indexing ----

    def index_child_chunks(
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
        """批量写入子切块到 ES（供 BM25 检索）。"""
        if not self.available:
            return 0
        if not child_chunks:
            return 0

        client = self._get_client()
        if client is None:
            return 0

        self._ensure_index(kb_id)
        index_name = self._index_name(kb_id)

        try:
            from elasticsearch.helpers import bulk

            actions = []
            for i, chunk in enumerate(child_chunks):
                extra = metadata_list[i] if metadata_list and i < len(metadata_list) else {}
                doc_id = f"doc_{document_id}_p{chunk.parent_index}_c{chunk.index}"
                actions.append({
                    "_op_type": "index",
                    "_index": index_name,
                    "_id": doc_id,
                    "_source": {
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
                        "topic": extra.get("topic", ""),
                        "keywords": extra.get("keywords", ""),
                    },
                })

            success, _ = bulk(client, actions, raise_on_error=False)
            logger.info(
                "ES BM25 写入完成: kb_id=%d, doc_id=%d, success=%d/%d",
                kb_id, document_id, success, len(child_chunks),
            )
            return success
        except Exception as e:
            logger.warning("ES BM25 写入失败: %s", e)
            return 0

    # ---- Search ----

    def search(
        self,
        kb_id: int,
        query: str,
        top_k: int = 10,
        user_id: int = 0,
        role: str = "USER",
        org_id: int = 0,
    ) -> list[dict[str, Any]]:
        """
        ES BM25 检索（含权限过滤）。

        Returns:
            [{id, content, score, metadata}, ...]
        """
        if not self.available:
            return []

        client = self._get_client()
        if client is None:
            return []

        index_name = self._index_name(kb_id)
        try:
            if not client.indices.exists(index=index_name):
                return []
        except Exception:
            return []

        # 构建权限过滤（与 Qdrant 保持一致）
        permission_filter = self._build_permission_filter(user_id, role, org_id)

        body = {
            "query": {
                "bool": {
                    "must": [
                        {
                            "multi_match": {
                                "query": query,
                                "fields": ["content^3", "topic^2", "keywords^2", "file_name"],
                                "type": "best_fields",
                                "tie_breaker": 0.3,
                            }
                        }
                    ],
                    "filter": permission_filter,
                }
            },
            "size": top_k,
            "_source": [
                "content", "document_id", "kb_id", "parent_id",
                "parent_index", "chunk_index", "file_name",
                "owner_id", "visibility", "org_id", "doc_version",
                "topic", "keywords",
            ],
        }

        try:
            resp = client.search(index=index_name, body=body)
            hits = resp.get("hits", {}).get("hits", [])
            results: list[dict[str, Any]] = []
            for hit in hits:
                src = hit.get("_source", {})
                results.append({
                    "id": hit.get("_id", ""),
                    "content": src.get("content", ""),
                    "score": float(hit.get("_score", 0.0)),
                    "metadata": src,
                })
            logger.debug(
                "ES BM25 检索: kb_id=%d, query='%s', results=%d",
                kb_id, query[:30], len(results),
            )
            return results
        except Exception as e:
            logger.warning("ES BM25 检索失败: %s", e)
            return []

    def _build_permission_filter(self, user_id: int, role: str, org_id: int) -> list:
        """构建 ES 权限过滤（bool filter）。ADMIN 不过滤。"""
        if role and role.upper() == "ADMIN":
            return []

        should_clauses = []
        if user_id != 0:
            should_clauses.append({"term": {"owner_id": user_id}})
        should_clauses.append({"term": {"visibility": "PUBLIC"}})
        if org_id != 0:
            should_clauses.append({
                "bool": {
                    "must": [
                        {"term": {"visibility": "ORG"}},
                        {"term": {"org_id": org_id}},
                    ]
                }
            })

        return [{"bool": {"should": should_clauses, "minimum_should_match": 1}}]

    # ---- Delete ----

    def delete_by_document_id(self, kb_id: int, document_id: int) -> int:
        """删除文档在 ES 中的所有子块。"""
        if not self.available:
            return 0
        client = self._get_client()
        if client is None:
            return 0
        index_name = self._index_name(kb_id)
        try:
            if not client.indices.exists(index=index_name):
                return 0
            body = {"query": {"term": {"document_id": document_id}}}
            resp = client.delete_by_query(index=index_name, body=body, refresh=True)
            deleted = resp.get("deleted", 0)
            logger.info("ES BM25 删除: kb_id=%d, doc_id=%d, deleted=%d",
                        kb_id, document_id, deleted)
            return deleted
        except Exception as e:
            logger.warning("ES BM25 删除失败: %s", e)
            return 0

    def delete_by_version(self, kb_id: int, document_id: int, old_version: int) -> int:
        """按版本号删除（增量重建场景）。"""
        if not self.available:
            return 0
        client = self._get_client()
        if client is None:
            return 0
        index_name = self._index_name(kb_id)
        try:
            if not client.indices.exists(index=index_name):
                return 0
            body = {
                "query": {
                    "bool": {
                        "must": [
                            {"term": {"document_id": document_id}},
                            {"term": {"doc_version": old_version}},
                        ]
                    }
                }
            }
            resp = client.delete_by_query(index=index_name, body=body, refresh=True)
            deleted = resp.get("deleted", 0)
            logger.info("ES BM25 版本删除: kb_id=%d, doc_id=%d, version=%d, deleted=%d",
                        kb_id, document_id, old_version, deleted)
            return deleted
        except Exception as e:
            logger.warning("ES BM25 版本删除失败: %s", e)
            return 0


# ============================================================
# Module-level convenience
# ============================================================

def get_es_bm25_client() -> ESBM25Client:
    """Return cached ESBM25Client singleton."""
    return ESBM25Client.get_instance()
