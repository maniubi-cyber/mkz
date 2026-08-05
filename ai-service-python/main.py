"""
Enterprise Knowledge Base RAG Q&A System — AI Service (FastAPI)

Entry point for the Python AI microservice.
Provides document parsing, embedding, vector search, and RAG chat.

Usage::

    python main.py              # start with .env defaults
    uvicorn main:app --reload   # dev mode
"""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

import uvicorn
from dotenv import load_dotenv
from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import (
    ai_router,
    create_health_router,
    documents_router,
)
from app.core.config import settings
from app.core.internal_auth import verify_internal_signature

# ============================================================
# Environment
# ============================================================

load_dotenv()

# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="[%(asctime)s] %(levelname)s %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


# ============================================================
# Lifespan (startup / shutdown events)
# ============================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler."""
    # ---- Startup ----
    logger.info("=" * 60)
    logger.info("[AI Service] Starting up ...")
    logger.info("[AI Service] Environment : %s", settings.ENV)
    logger.info("[AI Service] Port        : %s", settings.AI_SERVICE_PORT)
    logger.info("[AI Service] Embedding   : %s (dim=%d, device=%s)",
                settings.EMBEDDING_MODEL_NAME,
                settings.EMBEDDING_DIMENSION,
                settings.EMBEDDING_DEVICE)
    logger.info("[AI Service] LLM         : %s @ %s",
                settings.LLM_MODEL_NAME, settings.LLM_PROVIDER)
    logger.info("[AI Service] Qdrant      : %s:%d (gRPC prefer=%s)",
                settings.QDRANT_HOST, settings.QDRANT_PORT, settings.QDRANT_PREFER_GRPC)
    logger.info("[AI Service] MinIO       : %s (bucket=%s)",
                settings.MINIO_ENDPOINT, settings.MINIO_BUCKET)
    logger.info("=" * 60)

    # Initialize services (lazy, on first use)
    # - Embedding model will be loaded on first /ai/search or /ai/chat call
    # - Qdrant client will be connected on first vector operation
    # - LLM client will be configured on first /ai/chat call

    yield

    # ---- Shutdown ----
    logger.info("[AI Service] Shutting down ...")


# ============================================================
# FastAPI Application
# ============================================================

app = FastAPI(
    title="Knowledge RAG AI Service",
    description=(
        "AI microservice for the Enterprise Knowledge Base RAG Q&A System. "
        "Provides document parsing, text chunking, embedding, "
        "vector search, and LLM-powered RAG chat."
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.is_dev else None,
    redoc_url="/redoc" if settings.is_dev else None,
)

# ============================================================
# CORS Middleware
# ============================================================

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
    expose_headers=["X-Request-Id"],
)

# ============================================================
# Register Routers
# ============================================================

# Health check at root level（不校验内部签名，供 docker-compose 健康检查与前端探活）
app.include_router(create_health_router())

# AI 业务路由统一要求内部签名校验：
# 仅 Java 后端（携带 X-Internal-* 签名头）可调用，防内网伪造请求。
internal_dependencies = [Depends(verify_internal_signature)]

# AI document parsing routes: /ai/documents/*
app.include_router(documents_router, dependencies=internal_dependencies)

# AI routes: /ai/search, /ai/chat, /ai/models
app.include_router(ai_router, dependencies=internal_dependencies)

# ============================================================
# Root Redirect
# ============================================================

@app.get("/", include_in_schema=False)
async def root():
    """Redirect root to docs in dev, or return service info in prod."""
    if settings.is_dev:
        from fastapi.responses import RedirectResponse
        return RedirectResponse(url="/docs")
    return {
        "service": "knowledge-rag-ai-service",
        "version": "1.0.0",
        "status": "running",
    }

# ============================================================
# Entry Point
# ============================================================

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.AI_SERVICE_PORT,
        reload=settings.is_dev,
        log_level=settings.LOG_LEVEL.lower(),
    )
