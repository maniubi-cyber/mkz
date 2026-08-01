"""HITL（Human-in-the-Loop）人工审批服务。

高风险工具调用（如课程上架/下架、处理违规评论）在执行前需创建审批单，
等待人工审核通过后再继续执行。审批记录存储在 Redis 中，TTL 由
settings.approval_ttl_hours 控制，避免历史审批单无限堆积。
"""
import json
import time
import uuid
from enum import Enum
from typing import Optional

import redis

from app.config.settings import get_settings


class ApprovalStatus(Enum):
    """审批状态枚举。"""

    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
    EXECUTED = "EXECUTED"


class ApprovalService:
    """人工审批服务：创建审批单、查询状态、审批通过/拒绝、等待审批完成。"""

    # 轮询等待审批结果时的间隔（秒）
    _POLL_INTERVAL = 2

    def __init__(self):
        settings = get_settings()
        self._redis = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password,
            db=settings.redis_db,
            decode_responses=True,
        )
        self._prefix = settings.approval_prefix
        self._default_timeout = settings.approval_timeout_seconds
        self._ttl_seconds = settings.approval_ttl_hours * 3600

    def _key(self, approval_id: str) -> str:
        """拼接 Redis 完整 key。"""
        return f"{self._prefix}{approval_id}"

    def _load(self, approval_id: str) -> Optional[dict]:
        """从 Redis 读取审批记录，不存在或已过期返回 None。"""
        raw = self._redis.get(self._key(approval_id))
        if raw is None:
            return None
        return json.loads(raw)

    def _save(self, approval_id: str, record: dict) -> None:
        """写回审批记录，刷新 updated_at 与 TTL。"""
        record["updated_at"] = int(time.time())
        self._redis.set(
            self._key(approval_id),
            json.dumps(record, ensure_ascii=False),
            ex=self._ttl_seconds,
        )

    def create_approval(
        self,
        user_id: str,
        session_id: str,
        tool_name: str,
        tool_args: dict,
        description: str,
        jwt_token: str = "",
    ) -> str:
        """创建审批请求，返回 approval_id。

        审批记录以 JSON 形式写入 Redis：
        key = approval_prefix + approval_id
        value = {id, user_id, session_id, tool_name, tool_args, jwt_token,
                 description, status=PENDING, created_at, updated_at}
        TTL = approval_ttl_hours 小时
        """
        approval_id = uuid.uuid4().hex
        now = int(time.time())
        record = {
            "id": approval_id,
            "user_id": user_id,
            "session_id": session_id,
            "tool_name": tool_name,
            "tool_args": tool_args,
            "jwt_token": jwt_token,
            "description": description,
            "status": ApprovalStatus.PENDING.value,
            "created_at": now,
            "updated_at": now,
        }
        self._redis.set(
            self._key(approval_id),
            json.dumps(record, ensure_ascii=False),
            ex=self._ttl_seconds,
        )
        return approval_id

    def get_status(self, approval_id: str) -> ApprovalStatus:
        """查询审批状态。审批单不存在或已过期时安全返回 PENDING。"""
        record = self._load(approval_id)
        if record is None:
            return ApprovalStatus.PENDING
        try:
            return ApprovalStatus(record["status"])
        except (KeyError, ValueError):
            return ApprovalStatus.PENDING

    def get_detail(self, approval_id: str) -> dict:
        """查询审批详情。审批单不存在时返回空字典。"""
        record = self._load(approval_id)
        return record or {}

    def get_execution_context(self, approval_id: str) -> Optional[dict]:
        """取出审批通过后可执行工具所需的上下文（状态/工具名/参数/jwt）。

        不存在或已过期返回 None。
        """
        record = self._load(approval_id)
        if record is None:
            return None
        return {
            "status": record.get("status"),
            "tool_name": record.get("tool_name"),
            "tool_args": record.get("tool_args"),
            "jwt_token": record.get("jwt_token", ""),
        }

    def mark_executed(self, approval_id: str, result: dict) -> None:
        """将审批单标记为已执行并记录工具执行结果。"""
        record = self._load(approval_id)
        if record is None:
            return
        record["status"] = ApprovalStatus.EXECUTED.value
        record["execution_result"] = result
        self._save(approval_id, record)

    def approve(self, approval_id: str) -> bool:
        """审批通过，更新状态为 APPROVED。审批单不存在时返回 False。"""
        record = self._load(approval_id)
        if record is None:
            return False
        record["status"] = ApprovalStatus.APPROVED.value
        self._save(approval_id, record)
        return True

    def reject(self, approval_id: str) -> bool:
        """审批拒绝，更新状态为 REJECTED。审批单不存在时返回 False。"""
        record = self._load(approval_id)
        if record is None:
            return False
        record["status"] = ApprovalStatus.REJECTED.value
        self._save(approval_id, record)
        return True

    def get_pending_approvals(self, user_id: Optional[str] = None) -> list[dict]:
        """查询待审批列表。

        使用 Redis SCAN 匹配 approval_prefix*，可选按 user_id 过滤。
        """
        result: list[dict] = []
        for key in self._redis.scan_iter(match=f"{self._prefix}*", count=100):
            raw = self._redis.get(key)
            if raw is None:
                continue
            record = json.loads(raw)
            if record.get("status") != ApprovalStatus.PENDING.value:
                continue
            if user_id is not None and record.get("user_id") != user_id:
                continue
            result.append(record)
        return result

    def wait_for_approval(
        self, approval_id: str, timeout: Optional[int] = None
    ) -> ApprovalStatus:
        """轮询等待审批结果，超时返回 PENDING。

        timeout 默认 settings.approval_timeout_seconds，轮询间隔 2 秒。
        """
        timeout = self._default_timeout if timeout is None else timeout
        deadline = time.time() + timeout
        while time.time() < deadline:
            status = self.get_status(approval_id)
            if status is not ApprovalStatus.PENDING:
                return status
            time.sleep(self._POLL_INTERVAL)
        return ApprovalStatus.PENDING
