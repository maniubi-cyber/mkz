"""AI 对话路由：非流式对话、SSE 流式对话、清空会话记忆。

对应简历「对话记忆管理」与「ReAct 工具调用」：
- 非流式 / 流式对话由 ReactAgent 编排，记忆由 SlidingWindowMemory 维护；
- 高风险工具调用经 HITL 审批后执行。
路由前缀由 main.py 注册为 /chat，本文件内路径不再重复前缀。
"""
from __future__ import annotations

from fastapi import APIRouter, Header, Query
from sse_starlette.sse import EventSourceResponse

from app.dependencies import get_memory, get_react_agent

router = APIRouter()


@router.get("/simple")
async def simple_chat(
    message: str = Query(..., description="用户提问内容"),
    sessionId: str = Query(..., description="会话 ID"),
    userId: str = Query(..., description="用户 ID"),
    authorization: str = Header(..., description="Bearer <JWT>"),
):
    """非流式对话：调用 ReactAgent.chat 同步返回完整回答。"""
    jwt_token = authorization.replace("Bearer ", "")
    agent = get_react_agent()
    answer = await agent.chat(sessionId, userId, message, jwt_token)
    return {"answer": answer}


@router.get("/")
async def stream_chat(
    message: str = Query(..., description="用户提问内容"),
    sessionId: str = Query("1", description="会话 ID，默认 1"),
    userId: str = Query("1", description="用户 ID，默认 1"),
    authorization: str = Header(..., description="Bearer <JWT>"),
):
    """SSE 流式对话：通过 ReactAgent.stream 逐事件推送，前端实时渲染。"""
    jwt_token = authorization.replace("Bearer ", "")
    agent = get_react_agent()

    async def event_generator():
        # ReactAgent.stream 产出 SSE 事件字符串，透传给前端
        async for event in agent.stream(sessionId, userId, message, jwt_token):
            yield {"data": event}

    return EventSourceResponse(event_generator())


@router.delete("/{session_id}")
async def clear_history(
    session_id: str,
    userId: str = Query(..., description="用户 ID"),
):
    """清空指定会话的全部记忆（消息列表、语义摘要、checkpoint）。"""
    memory = get_memory()
    memory.clear(session_id, userId)
    return {"success": True}