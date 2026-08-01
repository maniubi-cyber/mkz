"""对话记忆管理：滑动窗口 + LLM 摘要压缩 + checkpoint 持久化。"""
from __future__ import annotations

import json
import logging
from typing import Optional

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_openai import ChatOpenAI

from app.config.settings import Settings, get_settings
from app.memory.redis_store import RedisStore

logger = logging.getLogger(__name__)

# 消息类型 -> BaseMessage 子类映射，用于反序列化
_MESSAGE_TYPE_MAP: dict[str, type[BaseMessage]] = {
    "human": HumanMessage,
    "ai": AIMessage,
    "system": SystemMessage,
}


def serialize_message(message: BaseMessage) -> str:
    """将 LangChain 消息序列化为 JSON 字符串。

    格式：{"type": "human", "content": "用户问题"}
    """
    return json.dumps(
        {"type": message.type, "content": message.content},
        ensure_ascii=False,
    )


def deserialize_message(msg_json: str) -> BaseMessage:
    """将 JSON 字符串反序列化为 LangChain 消息对象。

    根据 type 字段构造对应的 BaseMessage 子类，未知类型回退为 HumanMessage。
    """
    data = json.loads(msg_json)
    msg_type: str = data.get("type", "human")
    content: str = data.get("content", "")
    cls = _MESSAGE_TYPE_MAP.get(msg_type, HumanMessage)
    return cls(content=content)


def _extract_text(response: BaseMessage) -> str:
    """从 LLM 响应中提取纯文本内容（content 通常为 str，做兼容处理）。"""
    content = response.content
    return content if isinstance(content, str) else str(content)


class SlidingWindowMemory:
    """滑动窗口对话记忆管理器。

    三段式记忆策略，兼顾上下文质量与 token 成本：
    1. 滑动窗口 —— 仅保留最近 window_size*2 条消息参与上下文，控制 token 消耗。
    2. LLM 摘要压缩 —— 超出窗口的旧消息由 LLM 压缩为语义摘要，
       作为 SystemMessage 前置注入，保留长期记忆。
    3. Checkpoint 持久化 —— 定期快照消息列表，支持服务重启后的断线恢复。
    """

    def __init__(self, settings: Optional[Settings] = None) -> None:
        self._settings = settings or get_settings()
        self._store = RedisStore(self._settings)
        # 摘要专用 LLM：temperature=0 保证输出稳定可控
        self._summary_llm = ChatOpenAI(
            model=self._settings.llm_summary_model,
            base_url=self._settings.llm_base_url,
            api_key=self._settings.llm_api_key,
            temperature=0,
            max_tokens=self._settings.llm_max_tokens,
            timeout=self._settings.llm_timeout_seconds,
        )

    @property
    def window_size(self) -> int:
        """滑动窗口保留的轮数（每轮 = 1 条 human + 1 条 ai）。"""
        return self._settings.memory_window_size

    @property
    def summary_threshold(self) -> int:
        """触发摘要压缩的消息条数阈值。"""
        return self._settings.memory_summary_threshold

    def get_messages(self, session_id: str, user_id: str) -> list[BaseMessage]:
        """获取参与上下文的消息列表。

        返回顺序：[SystemMessage(摘要)] + 最近 window_size*2 条消息。
        若不存在摘要则仅返回窗口内消息。user_id 用于 API 一致性与日志追踪。
        """
        messages: list[BaseMessage] = []
        # 1. 前置语义摘要作为系统上下文
        summary = self._store.get_summary(session_id)
        if summary:
            messages.append(SystemMessage(content=summary))
        # 2. 读取最近 window_size*2 条消息并反序列化
        recent_jsons = self._store.get_recent_messages(
            session_id, self.window_size * 2
        )
        messages.extend(deserialize_message(m) for m in recent_jsons)
        return messages

    def add_message(self, session_id: str, user_id: str, message: BaseMessage) -> None:
        """追加一条消息，并按阈值触发摘要压缩与 checkpoint 保存。"""
        # 1. 序列化并存入 Redis List
        self._store.push_message(session_id, serialize_message(message))
        count = self._store.get_message_count(session_id)
        # 2. 消息总数超过阈值则压缩摘要
        if count > self.summary_threshold:
            self._compress_summary(session_id, user_id)
        # 3. 每隔 checkpoint_interval 条保存一次快照
        if count % self._settings.memory_checkpoint_interval == 0:
            self._save_checkpoint(session_id, user_id)

    def _compress_summary(self, session_id: str, user_id: str) -> None:
        """将超出窗口的旧消息压缩为语义摘要，并裁剪消息列表。"""
        total = self._store.get_message_count(session_id)
        keep_count = self.window_size * 2
        if total <= keep_count:
            return
        # 取出将被压缩的旧消息（列表前段，超出窗口的部分）
        all_jsons = self._store.get_messages(session_id)
        old_jsons = all_jsons[: total - keep_count]
        old_messages = [deserialize_message(m) for m in old_jsons]
        history_text = "\n".join(f"{m.type}: {m.content}" for m in old_messages)
        # 调用 LLM 生成语义摘要（真实 LLM 调用，非正则截断）
        prompt = (
            "请将以下对话历史压缩为简洁的语义摘要，"
            "保留关键信息、用户意图和已确认的事实：\n" + history_text
        )
        try:
            new_summary = _extract_text(self._summary_llm.invoke(prompt))
        except Exception as exc:
            logger.warning("会话 %s 摘要压缩失败，跳过本次压缩: %s", session_id, exc)
            return
        # 合并已有摘要，避免摘要无限膨胀
        existing = self._store.get_summary(session_id)
        if existing:
            final_summary = self._merge_summaries(existing, new_summary)
        else:
            final_summary = new_summary
        # 写回摘要并裁剪消息列表，仅保留最近窗口内的消息
        self._store.set_summary(session_id, final_summary)
        self._store.trim_messages(session_id, keep_count)
        logger.info(
            "会话 %s 摘要压缩完成，消息数 %d -> %d",
            session_id,
            total,
            keep_count,
        )

    def _merge_summaries(self, old: str, new: str) -> str:
        """合并新旧摘要，避免摘要无限增长。"""
        merged = f"历史摘要：{old}\n最新摘要：{new}"
        try:
            response = self._summary_llm.invoke(
                "请合并以下两段摘要为一段连贯的语义摘要，保留全部关键信息：\n"
                + merged
            )
            return _extract_text(response)
        except Exception as exc:
            logger.warning("摘要合并失败，使用拼接结果: %s", exc)
            return merged

    def _save_checkpoint(self, session_id: str, user_id: str) -> None:
        """将当前全部消息快照存入 Redis checkpoint。"""
        messages = self._store.get_messages(session_id)
        snapshot = json.dumps(messages, ensure_ascii=False)
        self._store.set_checkpoint(user_id, session_id, snapshot)
        logger.info(
            "会话 %s checkpoint 已保存（%d 条消息）",
            session_id,
            len(messages),
        )

    def restore_from_checkpoint(self, session_id: str, user_id: str) -> bool:
        """当消息列表为空时，从 checkpoint 恢复消息。

        Returns:
            True 表示恢复成功；False 表示无需恢复或无可用 checkpoint。
        """
        # 消息列表非空则无需恢复
        if self._store.get_message_count(session_id) > 0:
            return False
        checkpoint = self._store.get_checkpoint(user_id, session_id)
        if not checkpoint:
            return False
        messages: list[str] = json.loads(checkpoint)
        for msg_json in messages:
            self._store.push_message(session_id, msg_json)
        logger.info(
            "会话 %s 从 checkpoint 恢复 %d 条消息",
            session_id,
            len(messages),
        )
        return True

    def clear(self, session_id: str, user_id: str) -> None:
        """清空该会话的全部记忆（消息列表、语义摘要、checkpoint）。"""
        self._store.clear(session_id)
        self._store.delete_checkpoint(user_id, session_id)
        logger.info("会话 %s 记忆已清空", session_id)
