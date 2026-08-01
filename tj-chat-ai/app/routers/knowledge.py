"""知识库 RAG 路由：文档上传分块入库、知识库对话、文档删除。

对应简历「知识库检索」：Markdown 文档按 H2/H3 标题分块写入 Chroma，
对话时检索 top3 相关分块拼接上下文交由 LLM 回答，多租户按 user_id 隔离。
路由前缀由 main.py 注册为 /file，本文件内路径不再重复前缀。
"""
from __future__ import annotations

from fastapi import APIRouter, File, Form, Query, UploadFile

from app.dependencies import get_knowledge_service

router = APIRouter()


@router.post("/upload")
async def upload(
    file: UploadFile = File(..., description="Markdown 文件"),
    userId: str = Form(..., description="用户 ID"),
    docId: str = Form(..., description="文档 ID"),
):
    """上传 Markdown 文档：按标题分块入库，返回分块数。"""
    content = (await file.read()).decode("utf-8")
    ks = get_knowledge_service()
    count = ks.upload_markdown(userId, docId, content)
    return {"docId": docId, "chunks": count}


@router.get("/chat")
async def knowledge_chat(
    question: str = Query(..., description="知识库提问"),
    sessionId: str = Query(..., description="会话 ID"),
    userId: str = Query(..., description="用户 ID"),
):
    """知识库对话：检索相关分块并基于上下文回答，返回答案与引用列表。"""
    ks = get_knowledge_service()
    answer, refs = ks.chat(userId, sessionId, question)
    return {"answer": answer, "references": refs}


@router.delete("/{doc_id}")
async def delete_doc(
    doc_id: str,
    userId: str = Query(..., description="用户 ID"),
):
    """删除指定文档及其全部向量分块。"""
    ks = get_knowledge_service()
    count = ks.delete_doc(userId, doc_id)
    return {"docId": doc_id, "deleted": count}