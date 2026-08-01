"""FastAI 服务入口。"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config.settings import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：启动时初始化资源，关闭时释放。"""
    # 启动
    yield
    # 关闭：释放 HTTP 连接池
    from app.dependencies import get_java_client

    try:
        await get_java_client().close()
    except Exception:
        pass


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="慕课栈 AI 服务",
        description="FastAPI + LangChain + Chroma 智能助手，提供对话记忆管理、ReAct 工具调用与人工审批。",
        version="1.0.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 注册路由
    from app.routers import approval, chat, knowledge

    app.include_router(chat.router, prefix="/chat", tags=["AI 对话"])
    app.include_router(knowledge.router, prefix="/file", tags=["知识库 RAG"])
    app.include_router(approval.router, prefix="/approval", tags=["人工审批"])

    @app.get("/health", tags=["健康检查"])
    def health():
        return {"status": "UP", "service": "mkz-chat-ai"}

    return app


app = create_app()

if __name__ == "__main__":
    import uvicorn

    s = get_settings()
    uvicorn.run("app.main:app", host=s.app_host, port=s.app_port, reload=False)
