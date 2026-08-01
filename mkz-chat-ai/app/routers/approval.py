"""HITL 人工审批路由：查询待审批、审批详情、通过/拒绝。

对应简历「人工审批机制」：高风险工具调用（如课程上下架、违规评论处理）
执行前创建审批单，由人工审核通过后再继续执行；审批记录存于 Redis。
路由前缀由 main.py 注册为 /approval，本文件内路径不再重复前缀。
"""
from __future__ import annotations

from fastapi import APIRouter, Query

from app.dependencies import get_approval_service, get_tool_executor

router = APIRouter()


@router.get("/pending")
async def list_pending(
    userId: str = Query(None, description="按用户 ID 过滤，为空则返回全部待审批"),
):
    """查询待审批列表，可选按用户过滤。"""
    svc = get_approval_service()
    return svc.get_pending_approvals(userId)


@router.get("/{approval_id}")
async def get_detail(approval_id: str):
    """查询审批单详情。审批单不存在时返回空字典。"""
    svc = get_approval_service()
    return svc.get_detail(approval_id)


@router.put("/{approval_id}/approve")
async def approve(approval_id: str):
    """审批通过：更新状态为 APPROVED 并真正执行对应高级工具，闭合 HITL 链路。"""
    svc = get_approval_service()
    ok = svc.approve(approval_id)
    if not ok:
        return {"success": False}
    executor = get_tool_executor()
    result = await executor.approve_and_execute(approval_id)
    return {
        "success": True,
        "executed": result.success,
        "message": result.error_message or "操作已执行成功",
    }


@router.put("/{approval_id}/reject")
async def reject(approval_id: str):
    """审批拒绝，更新状态为 REJECTED。审批单不存在时返回 success=False。"""
    svc = get_approval_service()
    ok = svc.reject(approval_id)
    return {"success": ok}