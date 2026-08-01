"""中风险写操作工具：优惠券领取与点赞。

执行前需校验幂等（由编排层根据风险等级在调用前实施，避免重复领取/重复点赞）。
"""
from __future__ import annotations

from app.clients.java_client import JavaClient
from app.tools.base import ToolResult, ToolRiskLevel, risk_level


@risk_level(ToolRiskLevel.MEDIUM)
async def receive_coupon(java_client: JavaClient, coupon_id: int, jwt_token: str) -> ToolResult:
    """领取优惠券（写操作，需幂等）。

    调用 promotion-service 的 POST /prs/user-coupons/{coupon_id}/receive。
    """
    try:
        result = await java_client.receive_coupon(coupon_id, jwt_token)
    except RuntimeError as exc:
        return ToolResult(success=False, error_message=str(exc))
    return ToolResult(success=True, data=result)


@risk_level(ToolRiskLevel.MEDIUM)
async def like_course(java_client: JavaClient, biz_id: int, jwt_token: str) -> ToolResult:
    """给业务对象点赞（写操作，需幂等）。

    调用 remark-service 的 POST /rs/likes，请求体 {bizId, liked}。
    """
    try:
        result = await java_client.like_liked(biz_id, jwt_token)
    except RuntimeError as exc:
        return ToolResult(success=False, error_message=str(exc))
    return ToolResult(success=True, data=result)
