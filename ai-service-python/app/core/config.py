"""
Application Configuration (pydantic-settings)

Reads all configuration from environment variables / .env file.
All settings are typed and validated at startup.

RAG 智能问答架构（父子切块策略）:
- 文档元数据: MySQL
- 父子切块:
  - 父切块 (Parent Chunks, 段落级): Redis 缓存（命中后回溯提供完整上下文）
  - 子切块 (Child Chunks, 句子级): Qdrant 向量数据库（embedding 做检索入口）

Qdrant 优势:
- 生产级向量数据库，独立服务部署，支持水平扩展
- 内置 HNSW 索引 + payload 过滤，支持元数据权限过滤
- 支持 RRF 融合所需的高效召回
- 适合中小~大规模知识库（万~亿级向量）
"""

from __future__ import annotations

from functools import lru_cache
from typing import Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Global application settings loaded from .env / environment."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ---- Application ----
    ENV: str = Field(default="dev", description="运行环境: dev / test / prod")
    AI_SERVICE_PORT: int = Field(default=8000, description="服务端口")
    CORS_ORIGINS: str = Field(default="*", description="CORS 允许来源（逗号分隔）")
    LOG_LEVEL: str = Field(default="INFO", description="日志级别")

    # ---- MinIO Object Storage ----
    MINIO_ENDPOINT: str = Field(default="localhost:9000", description="MinIO 服务地址")
    MINIO_ACCESS_KEY: str = Field(default="minioadmin", description="MinIO Access Key")
    MINIO_SECRET_KEY: str = Field(default="minioadmin", description="MinIO Secret Key")
    MINIO_BUCKET: str = Field(default="knowledge-rag", description="MinIO Bucket 名称")
    MINIO_SECURE: bool = Field(default=False, description="是否使用 HTTPS")

    # ---- MySQL (文档元数据) ----
    MYSQL_HOST: str = Field(default="localhost", description="MySQL 主机")
    MYSQL_PORT: int = Field(default=3306, description="MySQL 端口")
    MYSQL_USER: str = Field(default="root", description="MySQL 用户名")
    MYSQL_PASSWORD: str = Field(default="", description="MySQL 密码")
    MYSQL_DATABASE: str = Field(default="knowledge_rag", description="MySQL 数据库名")

    # ---- Redis (子切块缓存) ----
    REDIS_HOST: str = Field(default="localhost", description="Redis 主机")
    REDIS_PORT: int = Field(default=6379, description="Redis 端口")
    REDIS_PASSWORD: str = Field(default="", description="Redis 密码")
    REDIS_DATABASE: int = Field(default=0, description="Redis 数据库编号")
    REDIS_CHILD_CHUNK_TTL: int = Field(
        default=86400,
        description="子切块缓存过期时间（秒），默认 24 小时"
    )

    # ---- Embedding Model ----
    EMBEDDING_MODEL_NAME: str = Field(
        default="BAAI/bge-large-zh-v1.5",
        description="sentence-transformers 模型名称或 HuggingFace 路径"
    )
    EMBEDDING_DEVICE: str = Field(
        default="cpu",
        description="推理设备: cpu / cuda / cuda:0"
    )
    EMBEDDING_DIMENSION: int = Field(
        default=1024,
        description="Embedding 向量维度"
    )
    EMBEDDING_BATCH_SIZE: int = Field(
        default=32,
        description="批量 embedding 大小"
    )
    EMBEDDING_NORMALIZE: bool = Field(
        default=True,
        description="是否对 embedding 做 L2 归一化"
    )

    # ---- Qdrant Vector Store (子切块 embedding 检索入口) ----
    QDRANT_HOST: str = Field(default="localhost", description="Qdrant 服务主机")
    QDRANT_PORT: int = Field(default=6333, description="Qdrant REST 端口")
    QDRANT_GRPC_PORT: int = Field(default=6334, description="Qdrant gRPC 端口")
    QDRANT_API_KEY: str = Field(default="", description="Qdrant API Key（可选，集群启用鉴权时填写）")
    QDRANT_COLLECTION_PREFIX: str = Field(
        default="kb_",
        description="Qdrant Collection 名称前缀（后跟 kb_id）"
    )
    QDRANT_DISTANCE: str = Field(
        default="Cosine",
        description="距离度量: Cosine / Euclid / Dot"
    )
    QDRANT_PREFER_GRPC: bool = Field(
        default=True,
        description="是否优先使用 gRPC（高吞吐场景推荐）"
    )

    # ---- Elasticsearch (BM25 双路召回 + 元数据权限过滤) ----
    ES_URIS: str = Field(default="http://localhost:9200", description="Elasticsearch 地址")
    ES_USERNAME: str = Field(default="", description="ES 用户名（可选）")
    ES_PASSWORD: str = Field(default="", description="ES 密码（可选）")
    ES_BM25_INDEX_PREFIX: str = Field(
        default="kb_",
        description="BM25 索引前缀（每个 kb 一个索引: kb_{kb_id}）"
    )
    ES_BM25_ENABLED: bool = Field(
        default=True,
        description="是否启用 ES BM25 检索路径（关闭则回退到本地 rank-bm25）"
    )
    ES_ANALYZER_IK: bool = Field(
        default=True,
        description="是否使用 IK 中文分词（未安装 IK 插件时置 False 回退 standard）"
    )

    # ---- LLM 元数据自动提取 ----
    LLM_METADATA_ENABLED: bool = Field(
        default=True,
        description="是否启用 LLM 自动提取切块元数据（主题/关键词/摘要）"
    )
    LLM_METADATA_BATCH_SIZE: int = Field(
        default=5,
        description="LLM 元数据提取批量大小（每次喂给 LLM 的切块数）"
    )

    # ---- LLM / Chat ----
    LLM_PROVIDER: str = Field(
        default="deepseek",
        description="LLM 提供商: deepseek / openai / qwen"
    )
    LLM_API_KEY: str = Field(default="", description="LLM API Key")
    LLM_BASE_URL: str = Field(
        default="https://api.deepseek.com/v1",
        description="LLM API Base URL（OpenAI 兼容）"
    )
    LLM_MODEL_NAME: str = Field(
        default="deepseek-chat",
        description="LLM 模型名称"
    )
    LLM_MAX_TOKENS: int = Field(default=2048, description="LLM 最大生成 token 数")
    LLM_TEMPERATURE: float = Field(default=0.7, description="LLM 生成温度")
    LLM_SYSTEM_PROMPT: str = Field(
        default=(
            "你是一个专业的企业知识库问答助手。请严格基于提供的参考文档回答问题。"
            "如果参考文档中没有相关信息，请明确告知用户，不要编造内容。"
            "回答时请引用具体的文档来源。"
        ),
        description="LLM 系统提示词"
    )

    # ---- RAG Parameters ----
    RAG_TOP_K: int = Field(default=5, description="检索返回的 top-k 片段数")
    RAG_SIMILARITY_THRESHOLD: float = Field(
        default=0.0,
        description=(
            "RRF 融合分数阈值（低于此值的结果被过滤）。"
            "RRF 分数 = 权重/(60+rank)，是排序值而非绝对相似度："
            "双路命中 rank-1 约 0.016，单路命中 rank-1 约 0.008，"
            "因此默认 0.0（仅靠 top_k 截断），调高需谨慎防误伤"
        )
    )
    RAG_RERANK_ENABLED: bool = Field(
        default=True,
        description="是否启用 BM25 重排序"
    )
    RAG_HYBRID_ALPHA: float = Field(
        default=0.5,
        description="混合检索权重: 0=纯BM25, 0.5=均等, 1=纯向量"
    )

    # ---- 父子切块配置 (Parent-Child Chunking) ----
    PARENT_CHUNK_SIZE: int = Field(
        default=1000,
        description="父切块大小（字符数），用于向量检索召回上下文"
    )
    CHILD_CHUNK_SIZE: int = Field(
        default=200,
        description="子切块大小（字符数），用于精确匹配和 LLM 输入"
    )
    CHUNK_OVERLAP: int = Field(default=50, description="相邻切片重叠字符数")
    CHUNK_SEPARATORS: str = Field(
        default="\n\n,\n,。,！,？,；,，",
        description="切片分隔符优先级（逗号分隔）"
    )
    CHUNK_MIN_SIZE: int = Field(default=50, description="最小切片大小（低于则合并）")

    # ---- LangChain Configuration ----
    LANGCHAIN_CHAIN_TYPE: str = Field(
        default="stuff",
        description="LangChain 链类型: stuff / refine / map_reduce / map_rerank"
    )

    # ---- API Timeouts (seconds) ----
    PARSE_TIMEOUT: int = Field(default=300, description="文档解析超时（秒）")
    SEARCH_TIMEOUT: int = Field(default=30, description="搜索超时（秒）")
    CHAT_TIMEOUT: int = Field(default=120, description="对话超时（秒）")

    # ---- Internal Auth (Java 后端内部调用鉴权) ----
    AI_INTERNAL_SECRET: str = Field(
        default="dev-internal-secret-change-me",
        description="内部调用共享密钥，与 Java 侧 ai.internal-secret 必须一致"
    )
    AI_INTERNAL_ENABLED: bool = Field(
        default=True,
        description="是否启用内部签名校验（测试环境可关闭）"
    )
    AI_INTERNAL_TOLERANCE_SECONDS: int = Field(
        default=300,
        description="签名时间戳容差（秒），容忍两端时钟偏差"
    )

    # ---- Connection Pool ----
    HTTP_POOL_MAX_SIZE: int = Field(default=20, description="HTTP 连接池最大连接数")
    HTTP_POOL_KEEP_ALIVE: int = Field(default=30, description="HTTP Keep-Alive 秒数")

    # ==================== Computed Properties ====================

    @property
    def minio_endpoint_clean(self) -> str:
        """MinIO endpoint without http/https prefix (for minio-py)."""
        return self.MINIO_ENDPOINT.replace("http://", "").replace("https://", "")

    @property
    def qdrant_url(self) -> str:
        """Qdrant REST URL."""
        return f"http://{self.QDRANT_HOST}:{self.QDRANT_PORT}"

    @property
    def redis_url(self) -> str:
        """Redis connection URL."""
        password_part = f":{self.REDIS_PASSWORD}@" if self.REDIS_PASSWORD else ""
        return f"redis://{password_part}{self.REDIS_HOST}:{self.REDIS_PORT}/{self.REDIS_DATABASE}"

    @property
    def mysql_dsn(self) -> str:
        """MySQL connection DSN (async)."""
        return (
            f"mysql+aiomysql://{self.MYSQL_USER}:{self.MYSQL_PASSWORD}"
            f"@{self.MYSQL_HOST}:{self.MYSQL_PORT}/{self.MYSQL_DATABASE}"
            f"?charset=utf8mb4"
        )

    @property
    def chunk_separator_list(self) -> list[str]:
        """Parse chunk separators from comma-delimited string."""
        return [
            s.strip()
            for s in self.CHUNK_SEPARATORS.split(",")
            if s.strip()
        ]

    @property
    def cors_origin_list(self) -> list[str]:
        """Parse CORS origins."""
        return [
            o.strip()
            for o in self.CORS_ORIGINS.split(",")
            if o.strip()
        ]

    @property
    def is_dev(self) -> bool:
        """Check if running in development mode."""
        return self.ENV.lower() in ("dev", "development")

    @property
    def is_prod(self) -> bool:
        """Check if running in production mode."""
        return self.ENV.lower() in ("prod", "production")


# ==================== Singleton ====================

@lru_cache()
def get_settings() -> Settings:
    """Return cached Settings singleton."""
    return Settings()


# Module-level instance for easy import
settings = get_settings()
