"""高风险管理工具：课程上下架与违规评论处理。

敏感操作，编排层须根据风险等级在执行前触发 Human-in-the-Loop 审批，
审批通过后方可调用本模块工具执行真实写操作。
"""
from __future__ import annotations

from app.clients.java_client import JavaClient
from app.tools.base import ToolResult, ToolRiskLevel, risk_level


@risk_level(ToolRiskLevel.HIGH)
async def course_updown(java_client: JavaClient, course_id: int, action: str, jwt_token: str) -> ToolResult:
    """课程上架/下架（敏感操作，需人工审批）。

    调用 course-service 的 PUT /cs/courses/{course_id}/{action}，action: up/down。
    """
    try:
        result = await java_client.course_updown(course_id, action, jwt_token)
    except RuntimeError as exc:
        return ToolResult(success=False, error_message=str(exc))
    return ToolResult(success=True, data=result)


@risk_level(ToolRiskLevel.HIGH)
async def handle_violation_comment(
    java_client: JavaClient, comment_id: int, action: str, jwt_token: str
) -> ToolResult:
    """处理违规评论：隐藏/删除（敏感操作，需人工审批）。

    调用 remark-service 的 PUT /rs/comments/{comment_id}/{action}，action: hide/delete。
    """
    try:
        result = await java_client.handle_violation_comment(comment_id, action, jwt_token)
    except RuntimeError as exc:
        return ToolResult(success=False, error_message=str(exc))
    return ToolResult(success=True, data=result)
