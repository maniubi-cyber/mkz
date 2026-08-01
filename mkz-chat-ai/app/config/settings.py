"""全局配置中心，基于 pydantic-settings 从环境变量/.env 加载。"""
from functools import lru_cache
from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # ===== LLM =====
    llm_base_url: str = "http://localhost:11434/v1"
    llm_api_key: str = "EMPTY"
    llm_model: str = "qwen3:0.6b"
    llm_temperature: float = 0.4
    llm_stream_temperature: float = 0.7
    llm_max_tokens: int = 1000
    llm_timeout_seconds: int = 15
    llm_max_retries: int = 3
    llm_summary_model: str = "qwen3:0.6b"

    # ===== Redis =====
    redis_host: str = "192.168.150.101"
    redis_port: int = 6379
    redis_password: str = "123321"
    redis_db: int = 0

    # ===== Chroma =====
    chroma_persist_directory: str = "./data/chroma"
    chroma_collection_name: str = "ai-chat"

    # ===== 记忆管理 =====
    memory_window_size: int = 10
    memory_summary_threshold: int = 20
    memory_checkpoint_interval: int = 5
    memory_summary_ttl_days: int = 7
    memory_checkpoint_ttl_days: int = 30

    # ===== HITL 审批 =====
    approval_timeout_seconds: int = 300
    approval_ttl_hours: int = 24

    # ===== Java 业务服务 =====
    java_gateway_url: str = "http://192.168.150.101:10010"
    java_gateway_token: str = ""

    # ===== 服务 =====
    app_host: str = "0.0.0.0"
    app_port: int = 8094

    # ===== Redis Key 前缀 =====
    @property
    def chat_memory_prefix(self) -> str:
        return "chat:memory:"

    @property
    def chat_summary_prefix(self) -> str:
        return "chat:summary:"

    @property
    def chat_checkpoint_prefix(self) -> str:
        return "chat:checkpoint:"

    @property
    def approval_prefix(self) -> str:
        return "chat:approval:"


@lru_cache
def get_settings() -> Settings:
    return Settings()
