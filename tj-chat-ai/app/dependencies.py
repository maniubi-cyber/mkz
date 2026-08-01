"""全局依赖装配：以 lru_cache 维护各组件单例，供路由层与编排层复用。

依赖链：
    JavaClient
      └─ ToolRegistry
           └─ ToolExecutor ──┐
 ApprovalService ────────────┘
 SlidingWindowMemory ──┐
                        └─ ReactAgent
 VectorStoreManager
   └─ KnowledgeService
"""
from __future__ import annotations

from functools import lru_cache

from app.agent.approval import ApprovalService
from app.agent.react_agent import ReactAgent
from app.agent.tool_executor import ToolExecutor
from app.clients.java_client import JavaClient
from app.memory.chat_memory import SlidingWindowMemory
from app.rag.knowledge_service import KnowledgeService
from app.rag.vector_store import VectorStoreManager
from app.tools.base import ToolRegistry


@lru_cache
def get_java_client() -> JavaClient:
    """Java 网关异步 HTTP 客户端单例。"""
    return JavaClient()


@lru_cache
def get_approval_service() -> ApprovalService:
    """HITL 人工审批服务单例（基于 Redis 持久化审批记录）。"""
    return ApprovalService()


@lru_cache
def get_tool_registry() -> ToolRegistry:
    """工具注册表单例，依赖 JavaClient 注入到各工具函数。"""
    return ToolRegistry(get_java_client())


@lru_cache
def get_tool_executor() -> ToolExecutor:
    """工具执行器单例，依赖 ToolRegistry 与 ApprovalService 完成风险分级执行。"""
    return ToolExecutor(get_tool_registry(), get_approval_service())


@lru_cache
def get_memory() -> SlidingWindowMemory:
    """对话记忆管理器单例（滑动窗口 + LLM 摘要 + checkpoint 持久化）。"""
    return SlidingWindowMemory()


@lru_cache
def get_react_agent() -> ReactAgent:
    """ReAct 智能体单例，依赖 SlidingWindowMemory 与 ToolExecutor 编排工具调用。"""
    return ReactAgent(get_memory(), get_tool_executor())


@lru_cache
def get_vector_store() -> VectorStoreManager:
    """Chroma 向量库管理器单例，多租户通过 metadata user_id 隔离。"""
    return VectorStoreManager()


@lru_cache
def get_knowledge_service() -> KnowledgeService:
    """知识库 RAG 服务单例，依赖 VectorStoreManager 实现检索增强对话。"""
    return KnowledgeService(get_vector_store())