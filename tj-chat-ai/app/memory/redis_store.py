"""Redis 存储封装，提供对话记忆、摘要、checkpoint 的持久化能力。"""
from __future__ import annotations

from typing import Optional

import redis

from app.config.settings import Settings, get_settings


class RedisStore:
    """Redis 操作工具类，封装对话记忆相关的键值读写。

    所有键均通过 settings 中的前缀拼接，保证命名统一可追溯：
    - 消息列表：  chat:memory:{session_id}
    - 语义摘要：  chat:summary:{session_id}
    - Checkpoint：chat:checkpoint:{user_id}:{session_id}
    """

    def __init__(self, settings: Optional[Settings] = None) -> None:
        self._settings = settings or get_settings()
        # decode_responses=True：Redis 返回 str 而非 bytes，省去逐处解码
        self._client = redis.Redis(
            host=self._settings.redis_host,
            port=self._settings.redis_port,
            password=self._settings.redis_password,
            db=self._settings.redis_db,
            decode_responses=True,
        )

    # ===== 键名构造 =====

    def _memory_key(self, session_id: str) -> str:
        """消息列表键。"""
        return self._settings.chat_memory_prefix + session_id

    def _summary_key(self, session_id: str) -> str:
        """语义摘要键。"""
        return self._settings.chat_summary_prefix + session_id

    def _checkpoint_key(self, user_id: str, session_id: str) -> str:
        """Checkpoint 快照键（按 user_id 隔离）。"""
        return self._settings.chat_checkpoint_prefix + user_id + ":" + session_id

    # ===== 消息列表 =====

    def get_messages(self, session_id: str) -> list[str]:
        """读取会话的全部消息（JSON 字符串列表）。"""
        return self._client.lrange(self._memory_key(session_id), 0, -1)

    def get_recent_messages(self, session_id: str, count: int) -> list[str]:
        """读取最近 count 条消息（基于 LRANGE 负索引，不足则返回全部）。"""
        if count <= 0:
            return []
        return self._client.lrange(self._memory_key(session_id), -count, -1)

    def push_message(self, session_id: str, msg_json: str) -> None:
        """向会话尾部追加一条消息（RPUSH）。"""
        self._client.rpush(self._memory_key(session_id), msg_json)

    def trim_messages(self, session_id: str, keep_count: int) -> None:
        """裁剪消息列表，仅保留最近 keep_count 条（LTRIM 负索引）。"""
        if keep_count <= 0:
            self._client.delete(self._memory_key(session_id))
            return
        self._client.ltrim(self._memory_key(session_id), -keep_count, -1)

    def get_message_count(self, session_id: str) -> int:
        """获取会话消息总数（LLEN）。"""
        return self._client.llen(self._memory_key(session_id))

    # ===== 语义摘要 =====

    def get_summary(self, session_id: str) -> Optional[str]:
        """读取会话的语义摘要，不存在时返回 None。"""
        return self._client.get(self._summary_key(session_id))

    def set_summary(self, session_id: str, summary: str) -> None:
        """写入语义摘要并设置 TTL（memory_summary_ttl_days 天）。"""
        ttl_seconds = self._settings.memory_summary_ttl_days * 24 * 60 * 60
        self._client.set(self._summary_key(session_id), summary, ex=ttl_seconds)

    # ===== Checkpoint =====

    def get_checkpoint(self, user_id: str, session_id: str) -> Optional[str]:
        """读取 checkpoint 快照，不存在时返回 None。"""
        return self._client.get(self._checkpoint_key(user_id, session_id))

    def set_checkpoint(self, user_id: str, session_id: str, data: str) -> None:
        """写入 checkpoint 快照并设置 TTL（memory_checkpoint_ttl_days 天）。"""
        ttl_seconds = self._settings.memory_checkpoint_ttl_days * 24 * 60 * 60
        self._client.set(
            self._checkpoint_key(user_id, session_id), data, ex=ttl_seconds
        )

    def delete_checkpoint(self, user_id: str, session_id: str) -> None:
        """删除 checkpoint 快照。"""
        self._client.delete(self._checkpoint_key(user_id, session_id))

    # ===== 清理 =====

    def clear(self, session_id: str) -> None:
        """清空该会话的消息列表与语义摘要。"""
        self._client.delete(
            self._memory_key(session_id),
            self._summary_key(session_id),
        )
