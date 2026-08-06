# ============================================================
# AI Service Dockerfile - FastAPI + LangChain（内置 embedding 模型）
# ============================================================

FROM python:3.11-slim

WORKDIR /app

# 系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# 依赖
COPY requirements.txt .
RUN pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 应用代码
COPY . .

# 内置 embedding 模型（约 95MB，避免运行时下载）
RUN mkdir -p /app/models

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# 单 worker：embedding 模型常驻内存，多 worker 会成倍占用
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
