"""
API Route Definitions

REST endpoints for the AI service.
共汇 RAG Pipeline:
- 文档解析: 父子切块 → LLM 元数据提取 → 父块入 Redis / 子块入 Qdrant + ES BM25
- 混合检索: ES BM25 + Qdrant 向量双路召回 → RRF 融合 → 元数据权限过滤 → 父块回溯
- 智能问答: LLM 改写 query → 混合检索 → SSE 流式生成 + 来源标注
- 评测: RAGAS (faithfulness / answer relevancy / context precision) + Langfuse
"""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, HTTPException

from app.schemas.requests import (
    ChatRequest,
    ParseRequest,
    SearchRequest,
    VectorDeleteRequest,
)
from app.schemas.responses import (
    ChatResult,
    ChunkInfo,
    ErrorResult,
    HealthResult,
    ModelsResult,
    ParseResult,
    ReferenceSource,
    SearchChunk,
    SearchResult,
)
from app.services.parser import (
    EmptyDocumentError,
    ParserError,
    UnsupportedFormatError,
    parse_document as parse_document_text,
)
from app.services.query_rewrite import rewrite_query
from app.services.retriever import get_retriever
from app.services.llm_client import get_llm_client
from app.services.prompt_builder import build_rag_prompt
from app.services.vector_store import get_vector_store
from app.services.evaluator import get_evaluator, EvaluationResult
from app.utils.minio_client import MinioClient

logger = logging.getLogger(__name__)

# ============================================================
# Router instances
# ============================================================

ai_router = APIRouter(prefix="/ai", tags=["AI"])
documents_router = APIRouter(prefix="/ai/documents", tags=["Documents"])


# ============================================================
# Health Check
# ============================================================

def create_health_router() -> APIRouter:
    """Create a health-check router mounted at the app root."""
    router = APIRouter(tags=["System"])

    @router.get("/health", response_model=HealthResult)
    async def health_check():
        return HealthResult(
            status="ok",
            service="knowledge-rag-ai-service",
            version="1.0.0",
        )

    return router


# ============================================================
# Document Parsing — Full Pipeline (Chapter 13 + BM25 rebuild)
# ============================================================

@documents_router.post(
    "/parse",
    response_model=ParseResult,
    summary="解析文档（父子切块 + LLM 元数据 + Qdrant + ES BM25）",
    description="""
    **完整处理流水线：**

    1. **MinIO 下载**
    2. **文本提取** (PDF/DOCX/XLSX/TXT/MD)
    3. **父子切块** (父块段落级 ~1000 / 子块句子级 ~200)
    4. **LLM 元数据提取** (topic / keywords)
    5. **父块写入 Redis** (回溯用)
    6. **子块 embedding 写入 Qdrant** (检索入口)
    7. **子块写入 ES BM25 索引** (IK 中文分词，双路召回)
    """,
    responses={
        200: {"description": "解析 + 父子切块 + 向量化 + BM25 索引完成"},
        400: {"model": ErrorResult},
        404: {"model": ErrorResult},
        500: {"model": ErrorResult},
    },
)
async def parse_document(request: ParseRequest):
    """
    Full document parsing pipeline.

    parse → 父子切块 → LLM 元数据 → 父块入 Redis / 子块入 Qdrant + ES BM25
    """
    t_total = time.perf_counter()

    logger.info(
        "解析请求: doc_id=%d, kb_id=%d, type=%s, file=%s",
        request.doc_id, request.kb_id, request.file_type, request.file_name,
    )

    # ---- Step 1: Download from MinIO ----
    t_step = time.perf_counter()
    minio = MinioClient.get_client()

    try:
        file_bytes = minio.download_file(request.minio_path)
    except FileNotFoundError:
        raise HTTPException(status_code=404,
                            detail=f"文件不存在于 MinIO: {request.minio_path}")
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=f"MinIO 下载失败: {e}")

    t_download = (time.perf_counter() - t_step) * 1000

    # ---- Step 2: Parse text ----
    t_step = time.perf_counter()

    try:
        raw_text = parse_document_text(file_bytes, request.file_type)
    except UnsupportedFormatError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except EmptyDocumentError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except ParserError as e:
        raise HTTPException(status_code=500, detail=str(e))

    t_parse = (time.perf_counter() - t_step) * 1000
    text_length = len(raw_text)

    # ---- Step 3-7: 父子切块 + LLM 元数据 + Qdrant + ES BM25（统一走 RAG 服务）----
    t_step = time.perf_counter()

    from app.services.langchain_rag_service import get_rag_service
    rag = get_rag_service()
    result = rag.index_document(
        document_id=request.doc_id,
        kb_id=request.kb_id,
        content=raw_text,
        file_name=request.file_name or "unknown",
        owner_id=request.owner_id,
        visibility=request.visibility,
        org_id=request.org_id,
        doc_version=1,
    )

    t_index = (time.perf_counter() - t_step) * 1000
    written = result.get("child_chunks", 0)

    # ---- Step 8: Summary ----
    t_total_elapsed = (time.perf_counter() - t_total) * 1000

    logger.info(
        "全流水线完成: doc_id=%d, 父块=%d 子块=%d, "
        "download=%.0fms parse=%.0fms index=%.0fms total=%.0fms",
        request.doc_id, result.get("parent_chunks", 0), written,
        t_download, t_parse, t_index, t_total_elapsed,
    )

    return ParseResult(
        doc_id=request.doc_id,
        status="SUCCESS",
        chunk_count=written,
        message=(
            f"全流水线完成 | "
            f"类型: {request.file_type.upper()} | "
            f"文本: {text_length} 字符 | "
            f"父块: {result.get('parent_chunks', 0)} / 子块: {written} | "
            f"下载: {t_download:.0f}ms | "
            f"解析: {t_parse:.0f}ms | "
            f"索引(LLM+Qdrant+ES): {t_index:.0f}ms | "
            f"总耗时: {t_total_elapsed:.0f}ms"
        ),
        text="",
    )


# ============================================================
# Vector Deletion
# ============================================================

@documents_router.post(
    "/vectors/delete",
    response_model=dict,
    summary="删除文档索引数据",
    description="删除指定文档在 Qdrant 子块 + Redis 父块 + ES BM25 索引中的所有数据。",
    responses={
        200: {"description": "删除成功"},
        400: {"model": ErrorResult},
    },
)
async def delete_vectors(request: VectorDeleteRequest):
    """Delete all index data for a document from Qdrant + Redis + ES BM25."""
    logger.info("删除索引数据: doc_id=%d, kb_id=%d", request.doc_id, request.kb_id)

    from app.services.langchain_rag_service import get_rag_service
    rag = get_rag_service()
    result = rag.delete_document(
        kb_id=request.kb_id, document_id=request.doc_id,
    )

    return {
        "status": "ok",
        "doc_id": request.doc_id,
        "kb_id": request.kb_id,
        "deleted_child_vectors": result.get("deleted_child_vectors", 0),
        "deleted_parent_chunks": result.get("deleted_parent_chunks", 0),
    }

# ============================================================
# Document Incremental Rebuild (编辑场景 - 版本号定位增量重建)
# ============================================================

@documents_router.post(
    "/rebuild",
    response_model=dict,
    summary="增量重建文档索引（编辑场景）",
    description="""
    **增量重建流水线（缓解频繁全量重构的 token 消耗）：**

    1. 读取 Redis 中文档当前版本号 `old_version`
    2. 仅删除 Qdrant 中 `old_version` 的子块（按版本号过滤，不影响并发其他版本）
    3. 以 `new_version = old_version + 1` 重新父子切块 + LLM 元数据提取 + 入库
    4. 父块写入 Redis（更新版本号）

    **适用场景：** 文档编辑后，定位变更版本仅重建受影响子块，避免全量重构。
    """,
    responses={
        200: {"description": "增量重建完成"},
        400: {"model": ErrorResult},
        500: {"model": ErrorResult},
    },
)
async def rebuild_document_incremental(request: ParseRequest):
    """Incremental rebuild: locate changed version then rebuild only affected chunks."""
    t_total = time.perf_counter()

    logger.info(
        "增量重建: doc_id=%d, kb_id=%d, type=%s, file=%s",
        request.doc_id, request.kb_id, request.file_type, request.file_name,
    )

    minio = MinioClient.get_client()
    try:
        file_bytes = minio.download_file(request.minio_path)
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail=f"文件不存在于 MinIO: {request.minio_path}")
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=f"MinIO 下载失败: {e}")

    try:
        raw_text = parse_document_text(file_bytes, request.file_type)
    except (UnsupportedFormatError, EmptyDocumentError) as e:
        raise HTTPException(status_code=400, detail=str(e))
    except ParserError as e:
        raise HTTPException(status_code=500, detail=str(e))

    from app.services.langchain_rag_service import get_rag_service
    rag = get_rag_service()
    result = rag.rebuild_document_incremental(
        document_id=request.doc_id,
        kb_id=request.kb_id,
        content=raw_text,
        file_name=request.file_name or "unknown",
        owner_id=request.owner_id,
        visibility=request.visibility,
        org_id=request.org_id,
    )

    elapsed = (time.perf_counter() - t_total) * 1000
    logger.info(
        "增量重建完成: doc_id=%d, old_version=%d, new_version=%d, "
        "删除旧子块=%d, 父块=%d 子块=%d, 耗时=%.0fms",
        request.doc_id, result.get("old_version", 0), result.get("new_version", 0),
        result.get("deleted_old_child_chunks", 0),
        result.get("parent_chunks", 0), result.get("child_chunks", 0), elapsed,
    )

    return {
        "status": "SUCCESS",
        "doc_id": request.doc_id,
        "old_version": result.get("old_version", 0),
        "new_version": result.get("new_version", 0),
        "deleted_old_child_chunks": result.get("deleted_old_child_chunks", 0),
        "parent_chunks": result.get("parent_chunks", 0),
        "child_chunks": result.get("child_chunks", 0),
        "elapsed_ms": round(elapsed, 2),
    }


# ============================================================
# Hybrid Search (Chapter 14 — IMPLEMENTED)
# ============================================================

@ai_router.post(
    "/search",
    response_model=SearchResult,
    summary="混合检索（向量 + BM25 → RRF 融合）",
    description="""
    **混合检索流水线：**

    1. **向量语义检索** — Embed query → Qdrant similarity search（payload 层权限过滤）
    2. **ES BM25 关键词检索** — ES IK 中文分词 → BM25 查询（双路召回）
    3. **RRF 融合** — Reciprocal Rank Fusion (k=60)
       - `score = alpha * vec_rrf + (1-alpha) * bm25_rrf`
       - alpha=0 → 纯 BM25, alpha=1 → 纯向量
    4. **权限过滤** — 基于 owner_id / visibility / org_id
       - ADMIN → 查看全部
       - owner → 查看自己上传的
       - PUBLIC → 任何人可见
       - ORG → 同组织可见
    5. **父块回溯** — 命中子块后回溯 Redis 父块，提供完整上下文
    6. **Top-K + 阈值过滤**

    **返回结果** 按 RRF 融合分数降序排列。
    """,
    responses={
        200: {"description": "检索完成"},
        400: {"model": ErrorResult},
    },
)
async def search(request: SearchRequest):
    """Hybrid search: vector + BM25 → RRF fusion → permission filter."""
    t_start = time.perf_counter()

    logger.info(
        "混合检索: query='%s', kb_id=%d, top_k=%d, alpha=%.2f, user=%d, role=%s",
        request.query[:50], request.kb_id, request.top_k,
        request.hybrid_alpha, request.user_id, request.role,
    )

    # ---- Query Rewrite (指代消解 + 口语化修正) ----
    # 与 /ai/chat 保持一致：检索链路的入口都应该走同一套 query 预处理，
    # 否则同一句提问在「搜索」和「问答」里会命中不同结果。
    search_query = request.query
    if request.enable_query_rewrite:
        search_query = rewrite_query(request.query)
        if search_query != request.query:
            logger.info(
                "Query rewritten: '%s' → '%s'", request.query, search_query
            )

    retriever = get_retriever()
    results = retriever.search(
        kb_id=request.kb_id,
        query=search_query,
        top_k=request.top_k,
        alpha=request.hybrid_alpha,
        similarity_threshold=request.similarity_threshold,
        user_id=request.user_id,
        role=request.role,
        org_id=request.org_id,
    )

    elapsed_ms = (time.perf_counter() - t_start) * 1000

    # Convert to response DTOs
    chunks = [
        SearchChunk(
            chunk=ChunkInfo(
                chunk_id=i,
                document_id=r.document_id,
                chunk_index=r.chunk_index,
                content=r.content[:500],  # snippet (first 500 chars)
                token_count=len(r.content),
            ),
            score=r.score,
            document_name=r.document_name,
            document_id=r.document_id,
        )
        for i, r in enumerate(results)
    ]

    logger.info(
        "混合检索完成: query='%s', results=%d, elapsed=%.1fms",
        request.query[:30], len(chunks), elapsed_ms,
    )

    return SearchResult(
        query=request.query,
        rewritten_query=search_query if search_query != request.query else None,
        kb_id=request.kb_id,
        total_hits=len(chunks),
        top_k=request.top_k,
        chunks=chunks,
        search_time_ms=round(elapsed_ms, 2),
    )


# ============================================================
# RAG Chat (Chapter 15 — IMPLEMENTED)
# ============================================================

@ai_router.post(
    "/chat",
    response_model=ChatResult,
    summary="RAG 智能问答",
    description="""
    **完整 RAG 对话流水线：**

    1. **混合检索** — HybridRetriever (向量 + BM25 → RRF → 权限过滤)
    2. **Prompt 组装** — PromptBuilder
       - System Prompt: 角色定义 + 规则约束 + 引用格式
       - 参考资料: 检索到的相关片段（编号 + 来源 + 相关度）
       - 对话历史: 前几轮 user/assistant 消息
       - 用户提问: 当前问题
    3. **LLM 生成** — DeepSeek (OpenAI 兼容)
       - temperature=0.3（知识问答场景低温度）
       - 返回 answer + token_usage

    **System Prompt 核心规则：**
    - 严格基于参考资料回答
    - 信息不足时明确说明
    - 禁止编造任何内容
    - 引用来源格式: `[来源N]`
    """,
    responses={
        200: {"description": "对话完成"},
        400: {"model": ErrorResult, "description": "参数错误"},
        500: {"model": ErrorResult, "description": "LLM 调用失败"},
    },
)
async def chat(request: ChatRequest):
    """
    RAG Chat — full implementation.

    retrieve → build prompt → LLM generate → return answer + sources
    """
    t_total = time.perf_counter()

    logger.info(
        "RAG 对话: kb_id=%d, question='%s', history=%d turns, user=%d",
        request.kb_id, request.question[:50], len(request.history),
        request.user_id,
    )

    # ---- Step 0: Query Rewrite (anaphora resolution + colloquial→formal) ----
    history_dicts = [
        {"role": h.role, "content": h.content}
        for h in request.history
    ] if request.history else []

    rewritten_query = rewrite_query(request.question, history=history_dicts)
    if rewritten_query != request.question:
        logger.info("Query rewritten: '%s' → '%s'", request.question, rewritten_query)

    # ---- Step 1: Retrieve relevant chunks ----
    t_search = time.perf_counter()

    retriever = get_retriever()
    retrieved = retriever.search(
        kb_id=request.kb_id,
        query=rewritten_query,
        top_k=request.top_k,
        user_id=request.user_id,
        role=request.role,
        org_id=request.org_id,
    )

    t_search_ms = (time.perf_counter() - t_search) * 1000
    logger.info("检索完成: %d chunks, %.0fms", len(retrieved), t_search_ms)

    if not retrieved:
        return ChatResult(
            answer="根据现有资料，我无法回答这个问题。知识库中未检索到相关信息。",
            kb_id=request.kb_id,
            conversation_id=request.conversation_id,
            sources=[],
            token_usage={},
            search_time_ms=round(t_search_ms, 2),
            generation_time_ms=0,
        )

    # ---- Step 2: Build RAG prompt (use rewritten query) ----
    messages = build_rag_prompt(
        question=rewritten_query,
        contexts=retrieved,
        history=history_dicts,
    )

    # ---- Step 3: Call LLM ----
    t_gen = time.perf_counter()

    try:
        llm = get_llm_client()
        answer, token_usage = llm.chat(
            messages=messages,
            temperature=request.temperature,
        )
    except Exception as e:
        logger.error("LLM 调用失败: %s", e)
        raise HTTPException(status_code=500, detail=f"LLM 调用失败: {e}")

    t_gen_ms = (time.perf_counter() - t_gen) * 1000

    # ---- Step 4: Build sources list ----
    sources = [
        ReferenceSource(
            document_id=r.document_id,
            document_name=r.document_name or f"文档#{r.document_id}",
            chunk_index=r.chunk_index,
            content_snippet=r.content[:500],
            score=r.score,
        )
        for r in retrieved
    ]

    t_total_ms = (time.perf_counter() - t_total) * 1000

    logger.info(
        "RAG 对话完成: answer_len=%d, sources=%d, "
        "search=%.0fms, gen=%.0fms, total=%.0fms, tokens=%s",
        len(answer), len(sources),
        t_search_ms, t_gen_ms, t_total_ms, token_usage,
    )

    return ChatResult(
        answer=answer,
        kb_id=request.kb_id,
        conversation_id=request.conversation_id,
        sources=sources,
        token_usage=token_usage,
        search_time_ms=round(t_search_ms, 2),
        generation_time_ms=round(t_gen_ms, 2),
        rewritten_query=rewritten_query,
    )


# ============================================================
# RAG Chat Stream (SSE — Streaming + Source Citation)
# ============================================================

@ai_router.post(
    "/chat/stream",
    summary="RAG 智能问答（SSE 流式输出 + 来源标注）",
    description="""
    **流式 RAG 对话接口：**

    1. **Query 改写** — 指代消解 + 口语化修正
    2. **混合检索** — HybridRetriever (向量 + BM25 → RRF → 权限过滤)
    3. **SSE 流式推送** — 每个 LLM token 实时推送
    4. **来源标注** — 首帧推送检索到的来源信息，便于数据溯源

    **SSE 事件格式：**
    - `sources` — 检索来源列表（JSON），在生成开始前推送
    - `content` — LLM 生成的文本片段（逐 token 推送）
    - `done` — 生成结束，附带 token 用量统计
    - `error` — 错误信息
    """,
)
async def chat_stream(request: ChatRequest):
    """
    RAG Chat with SSE streaming output + source citations.
    """
    from fastapi.responses import StreamingResponse

    t_total = time.perf_counter()

    logger.info(
        "RAG 流式对话: kb_id=%d, question='%s', history=%d turns, user=%d",
        request.kb_id, request.question[:50], len(request.history),
        request.user_id,
    )

    # ---- Step 0: Query Rewrite ----
    history_dicts = [
        {"role": h.role, "content": h.content}
        for h in request.history
    ] if request.history else []

    rewritten_query = rewrite_query(request.question, history=history_dicts)
    if rewritten_query != request.question:
        logger.info("Query rewritten: '%s' → '%s'", request.question, rewritten_query)

    # ---- Step 1: Retrieve relevant chunks ----
    t_search = time.perf_counter()

    retriever = get_retriever()
    retrieved = retriever.search(
        kb_id=request.kb_id,
        query=rewritten_query,
        top_k=request.top_k,
        user_id=request.user_id,
        role=request.role,
        org_id=request.org_id,
    )

    t_search_ms = (time.perf_counter() - t_search) * 1000
    logger.info("检索完成: %d chunks, %.0fms", len(retrieved), t_search_ms)

    def _sse_event(event: str, data: str) -> str:
        """Format a single SSE event line."""
        return f"event: {event}\ndata: {data}\n\n"

    def _json_sse(event: str, obj: Any) -> str:
        """Format a SSE event with JSON data."""
        import json
        return f"event: {event}\ndata: {json.dumps(obj, ensure_ascii=False)}\n\n"

    async def _stream_generator():
        try:
            # ---- Emit sources first ----
            if retrieved:
                source_events = [
                    {
                        "document_id": r.document_id,
                        "document_name": r.document_name or f"文档#{r.document_id}",
                        "chunk_index": r.chunk_index,
                        "score": r.score,
                        "content_snippet": r.content[:300],
                    }
                    for r in retrieved
                ]
                yield _json_sse("sources", source_events)
            else:
                yield _json_sse("sources", [])
                # No retrieval result — emit a no-result message and finish
                yield _json_sse("content", "根据现有资料，我无法回答这个问题。知识库中未检索到相关信息。")
                yield _json_sse("done", {
                    "token_usage": {},
                    "search_time_ms": round(t_search_ms, 2),
                    "generation_time_ms": 0,
                    "total_time_ms": round((time.perf_counter() - t_total) * 1000, 2),
                })
                return

            # ---- Step 2: Build RAG prompt ----
            messages = build_rag_prompt(
                question=rewritten_query,
                contexts=retrieved,
                history=history_dicts,
            )

            # ---- Step 3: Stream LLM output ----
            t_gen = time.perf_counter()
            llm = get_llm_client()

            accumulated = []
            token_usage: dict[str, int] = {}
            generator = None

            try:
                generator = llm.chat_stream(
                    messages=messages,
                    temperature=request.temperature,
                )
                for chunk in generator:
                    accumulated.append(chunk)
                    yield _json_sse("content", chunk)

                # 通过 generator.close() 捕获 token usage
                try:
                    token_usage = generator.close()
                except (StopIteration, AttributeError):
                    token_usage = {}

                yield _json_sse("done", {
                    "token_usage": token_usage,
                    "search_time_ms": round(t_search_ms, 2),
                    "generation_time_ms": round(time.perf_counter() - t_gen, 2),
                    "total_time_ms": round((time.perf_counter() - t_total) * 1000, 2),
                })
            except Exception as e:
                logger.error("LLM 流式调用失败: %s", e)
                yield _json_sse("error", str(e))

        except Exception as e:
            logger.error("SSE 流式对话异常: %s", e, exc_info=True)
            try:
                yield _json_sse("error", f"服务端异常: {e}")
            except Exception:
                pass

    return StreamingResponse(
        _stream_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # Disable Nginx buffering
        },
    )


# ============================================================
# Models Info
# ============================================================

@ai_router.get(
    "/models",
    response_model=ModelsResult,
    summary="获取可用模型列表",
)
async def list_models():
    """List available embedding and LLM models."""
    from app.schemas.responses import ModelInfo

    return ModelsResult(
        embedding_models=[
            ModelInfo(name="bge-large-zh-v1.5", provider="BAAI", dimension=1024),
            ModelInfo(name="text2vec-large-chinese", provider="shibing624", dimension=1024),
        ],
        llm_models=[
            ModelInfo(name="deepseek-chat", provider="DeepSeek"),
            ModelInfo(name="qwen-turbo", provider="Alibaba"),
            ModelInfo(name="gpt-4o", provider="OpenAI"),
        ],
    )


# ============================================================
# RAG Evaluation
# ============================================================

@ai_router.post(
    "/evaluate",
    summary="RAG 评测（RAGAS + Langfuse）",
    description="""
    **RAG 质量评测接口：**

    使用 RAGAS 框架评估 RAG 系统的三项核心指标：
    - **Faithfulness (忠实度)**: 回答是否基于检索到的上下文
    - **Answer Relevancy (答案相关性)**: 回答与问题的相关程度
    - **Context Precision (上下文精确度)**: 检索到的上下文是否精确有用

    结果会同步追踪到 Langfuse 平台，支持可视化趋势分析。

    **注意**: 需要安装 ragas 和 langfuse 依赖才能使用完整功能。
    """,
)
async def evaluate_rag(request: ChatRequest):
    """
    RAG evaluation using RAGAS metrics.
    """
    logger.info("RAG 评测请求: question='%s'", request.question[:50])

    # Step 1: Query Rewrite
    history_dicts = [
        {"role": h.role, "content": h.content}
        for h in request.history
    ] if request.history else []

    rewritten_query = rewrite_query(request.question, history=history_dicts)

    # Step 2: Retrieve
    retriever = get_retriever()
    retrieved = retriever.search(
        kb_id=request.kb_id,
        query=rewritten_query,
        top_k=request.top_k,
        user_id=request.user_id,
        role=request.role,
        org_id=request.org_id,
    )

    contexts = [r.content for r in retrieved]

    # Step 3: Generate answer (non-streaming for evaluation)
    messages = build_rag_prompt(
        question=rewritten_query,
        contexts=retrieved,
        history=history_dicts,
    )

    try:
        llm = get_llm_client()
        answer, _ = llm.chat(messages=messages, temperature=request.temperature)
    except Exception as e:
        logger.error("LLM 调用失败: %s", e)
        raise HTTPException(status_code=500, detail=f"LLM 调用失败: {e}")

    # Step 4: Evaluate
    evaluator = get_evaluator()
    result = evaluator.evaluate_single(
        question=request.question,
        contexts=contexts,
        answer=answer,
    )

    return {
        "question": request.question,
        "rewritten_query": rewritten_query,
        "answer": answer,
        "contexts": contexts,
        "faithfulness": round(result.faithfulness, 4),
        "answer_relevancy": round(result.answer_relevancy, 4),
        "context_precision": round(result.context_precision, 4),
        "overall_score": round(result.overall_score, 4),
        "details": result.details,
    }


# ============================================================
# Batch Evaluation
# ============================================================

@ai_router.post(
    "/evaluate/batch",
    summary="批量 RAG 评测",
    description="""
    **批量 RAG 评测接口：**

    对多条 (question, answer, contexts) 三元组进行批量评测，
    返回每项指标的平均分和建议。
    """,
)
async def evaluate_batch(request: dict):
    """
    Batch RAG evaluation.
    """
    evaluations = request.get("evaluations", [])
    if not evaluations:
        raise HTTPException(status_code=400, detail="evaluations 不能为空")

    logger.info("批量 RAG 评测: %d 条样本", len(evaluations))

    evaluator = get_evaluator()
    results = []
    for i, item in enumerate(evaluations):
        question = item.get("question", "")
        contexts = item.get("contexts", [])
        answer = item.get("answer", "")

        if not question or not answer:
            continue

        result = evaluator.evaluate_single(question, contexts, answer)
        results.append(result.to_dict())

    # Generate summary report
    report = evaluator.generate_report(results)

    return {
        "total_evaluations": len(results),
        "report": report,
        "results": results,
    }
