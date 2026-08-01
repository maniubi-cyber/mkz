"""低风险只读工具：课程查询与排行榜。

直接执行，返回 Java 服务的真实数据以防止 LLM 编造结果。
"""
from __future__ import annotations

from app.clients.java_client import JavaClient
from app.tools.base import ToolResult, ToolRiskLevel, risk_level


@risk_level(ToolRiskLevel.LOW)
async def query_course(java_client: JavaClient, name: str, jwt_token: str) -> ToolResult:
    """按名称查询课程列表（只读）。

    调用 course-service 的 /cs/courses/simpleInfo/list，返回真实课程数据。
    """
    try:
        courses = await java_client.query_courses_by_name(name, jwt_token)
    except RuntimeError as exc:
        return ToolResult(success=False, error_message=str(exc))
    # 返回服务端真实数据，防止 LLM 编造课程信息
    return ToolResult(success=True, data=courses)


@risk_level(ToolRiskLevel.LOW)
async def get_ranking(java_client: JavaClient, top_n: int, jwt_token: str) -> ToolResult:
    """获取课程排行榜（只读）。

    调用 course-service 的 /cs/courses/ranking?topN={top_n}。
    """
    try:
        ranking = await java_client.get_course_ranking(top_n, jwt_token)
    except RuntimeError as exc:
        return ToolResult(success=False, error_message=str(exc))
    return ToolResult(success=True, data=ranking)
