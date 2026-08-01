"""工具基础设施：风险等级、装饰器、结果结构与工具注册表。

风险等级决定编排层（ReAct / Human-in-the-Loop）的执行策略：
    LOW    只读操作，直接执行，对结果校验防编造
    MEDIUM 写操作，执行前校验幂等
    HIGH   敏感操作，触发 Human-in-the-Loop 审批
"""
from __future__ import annotations

from enum import IntEnum
from typing import Any, Callable

from app.clients.java_client import JavaClient


class ToolRiskLevel(IntEnum):
    """工具风险等级。"""

    LOW = 1  # 只读操作，直接执行，对结果校验防编造
    MEDIUM = 2  # 写操作，执行前校验幂等
    HIGH = 3  # 敏感操作，触发 Human-in-the-Loop 审批


def risk_level(level: ToolRiskLevel) -> Callable[[Callable], Callable]:
    """装饰器：为工具函数标记风险等级。

    标记后可通过 ``func._risk_level`` 读取，供注册表与编排层使用。
    """

    def decorator(func: Callable) -> Callable:
        func._risk_level = level  # type: ignore[attr-defined]
        return func

    return decorator


class ToolResult:
    """工具执行的结构化结果。

    approval_status 取值：PENDING / APPROVED / REJECTED / NOT_REQUIRED。
    对于 LOW/MEDIUM 工具或 HIGH 工具审批通过后实际执行的场景，
    approval_status 默认 NOT_REQUIRED；编排层在触发审批时使用 PENDING 等状态。
    """

    # 审批状态常量
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
    NOT_REQUIRED = "NOT_REQUIRED"

    def __init__(
        self,
        success: bool,
        data: Any = None,
        error_message: str = "",
        requires_approval: bool = False,
        approval_id: str = "",
        approval_status: str = NOT_REQUIRED,
    ) -> None:
        self.success: bool = success
        self.data: Any = data
        self.error_message: str = error_message
        self.requires_approval: bool = requires_approval
        self.approval_id: str = approval_id
        self.approval_status: str = approval_status

    def to_dict(self) -> dict:
        """转换为可序列化字典，便于日志与接口返回。"""
        return {
            "success": self.success,
            "data": self.data,
            "error_message": self.error_message,
            "requires_approval": self.requires_approval,
            "approval_id": self.approval_id,
            "approval_status": self.approval_status,
        }


class ToolRegistry:
    """工具注册表：管理工具函数的注册、查找与统一调用。

    初始化时注册所有内置工具，工具函数内部通过注入的 java_client 调用 Java 网关。
    """

    def __init__(self, java_client: JavaClient) -> None:
        self.java_client: JavaClient = java_client
        self._tools: dict[str, tuple[Callable, ToolRiskLevel]] = {}
        self._register_defaults()

    def _register_defaults(self) -> None:
        """注册内置工具。延迟导入以避免与各工具模块的循环依赖。"""
        from app.tools import admin_tools, course_tools, coupon_tools

        # 只读工具（低风险）
        self.register("query_course", course_tools.query_course)
        self.register("get_ranking", course_tools.get_ranking)
        # 写操作工具（中风险）
        self.register("receive_coupon", coupon_tools.receive_coupon)
        self.register("like_course", coupon_tools.like_course)
        # 敏感操作工具（高风险）
        self.register("course_updown", admin_tools.course_updown)
        self.register("handle_violation_comment", admin_tools.handle_violation_comment)

    def register(self, name: str, func: Callable) -> None:
        """注册工具函数，自动读取其风险等级（未标记则默认 LOW）。"""
        self._tools[name] = (func, getattr(func, "_risk_level", ToolRiskLevel.LOW))

    def get(self, name: str) -> tuple[Callable, ToolRiskLevel]:
        """按名称获取工具函数及其风险等级。"""
        if name not in self._tools:
            raise KeyError(f"未注册的工具：{name}")
        return self._tools[name]

    def list_tools(self) -> list[str]:
        """列出所有已注册工具名称。"""
        return list(self._tools.keys())

    async def invoke(self, name: str, *args: Any, **kwargs: Any) -> ToolResult:
        """调用工具，自动注入 java_client 作为第一个参数。

        编排层调用前可根据 get() 返回的风险等级决定是否需要幂等校验或人工审批。
        """
        func, _level = self.get(name)
        return await func(self.java_client, *args, **kwargs)
