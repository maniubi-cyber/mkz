# 共汇 - 团队文档协作平台

## 项目简介

共汇是一个用于团队协作的文档管理平台，旨在解决团队内部文档分散、搜索效率低、知识复用难等问题。在传统文档管理基础上引入 RAG（检索增强生成）能力，实现基于文档内容的 AI 智能问答，让知识获取从"关键词搜索"升级为"自然语言问答"。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.0 | Java 后端框架 |
| Elasticsearch | 8.13.0 | 全文检索（IK 中文分词 + 高亮）+ BM25 双路召回 |
| Redis | 7 | 父切块存储（命中回溯提供完整上下文）+ 缓存 |
| MySQL | 8.0 | 文档元数据存储（MyBatis-Plus + 乐观锁） |
| FastAPI | 0.111+ | Python AI 服务框架 |
| LangChain | 0.2+ | RAG 框架 |
| Qdrant | 1.9+ | 向量数据库（子块 embedding 检索入口） |
| MinIO | - | 文件对象存储 |
| WebSocket | - | 实时协作编辑（ot.js + 光标同步） |

---

## 项目架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue + ot.js)                        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Java 后端服务 (Spring Boot 3.3)                 │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐    │
│  │ 异步编排   │ │ ES 搜索    │ │ 文档导出   │ │ 协同编辑   │    │
│  │ Completabl │ │ IK 分词    │ │ 策略+工厂  │ │ OT+WS      │    │
│  │ eFuture    │ │ 高亮显示   │ │ 模式       │ │            │    │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  AiServiceClient (RestTemplate) → Python AI 服务          │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐              ┌─────────────────────────────┐
│   MySQL + ES    │              │   Python AI 服务 (FastAPI)   │
│   Redis + MinIO │◄─RestTemplate─│   LangChain RAG Pipeline    │
│                 │              │   Qdrant(向量) + Redis(父块) │
└─────────────────┘              └─────────────────────────────┘
```

---

## 项目成果

### 1. 父子切块策略 + LLM 元数据 + 版本号增量重建

文档内容写入采用 **父子切块策略**：父块保留段落语义（~1000 字符）、子块切至句子级（~200 字符），并由 LLM 自动提取元数据（topic / keywords）。子块 embedding 存入 Qdrant 做检索入口，命中后回溯父块提供完整上下文，平衡检索速度与内容关联。文档编辑通过元数据内版本号定位增量重建，缓解频繁大量重构的 token 消耗。

| 切块类型 | 大小 | 存储 | 用途 |
|----------|------|------|------|
| 父切块 | ~1000 字符 | Redis | 命中子块后回溯，提供完整段落上下文 |
| 子切块 | ~200 字符 | Qdrant | embedding 检索入口，payload 携带元数据 + 版本号 |

**增量重建流程：**
1. 读取 Redis 中文档当前版本号 `old_version`
2. 仅删除 Qdrant 中 `old_version` 的子块（按版本号过滤，不影响并发其他版本）
3. 以 `new_version = old_version + 1` 重新父子切块 + LLM 元数据提取 + 入库

---

### 2. ES BM25 + Qdrant 向量双路召回 + RRF 融合

针对单一检索路径的召回盲区（向量检索漏精确关键词、关键词检索漏语义改写），设计 **ES BM25 + Qdrant 向量双路召回**，通过 RRF（Reciprocal Rank Fusion）融合排序取 top-k，并叠加元数据权限过滤，确保用户仅命中有权限的文档。用户 query 进入检索前经过 LLM 轻量改写（指代消解、口语化表述修正），提升复杂 query 下的召回率。

```
用户 Query
    │
    ▼
LLM 轻量改写（指代消解 / 口语化修正）
    │
    ├──────────────────┬──────────────────┐
    ▼                  ▼
Qdrant 向量检索     ES BM25 检索
(payload 权限过滤)  (IK 中文分词)
    │                  │
    └────────┬─────────┘
             ▼
      RRF 融合排序 (k=60)
             │
             ▼
      父块回溯 (Redis)
             │
             ▼
       Top-K 结果
```

**权限过滤规则：** ADMIN 查看全部 / owner 查看自己 / PUBLIC 任何人可见 / ORG 同组织可见。

---

### 3. LLM SSE 流式输出 + RAGAS 评测

LLM 生成通过 **SSE 流式输出**且携带来源标注，方便数据溯源提升数据真实性并提升用户体验。基于 **RAGAS + Langfuse** 构建评测流程，覆盖 faithfulness / answer relevancy / context precision 三项指标，每次调整切块策略、检索参数或 prompt 后评测对比，确保改动可量化、可回退。

| 指标 | 含义 |
|------|------|
| Faithfulness | 回答是否忠实基于检索到的上下文（不编造） |
| Answer Relevancy | 回答与问题的相关程度 |
| Context Precision | 检索到的上下文是否精确有用 |

**SSE 事件格式：** sources（检索来源）→ content（逐 token 生成）→ done（token 用量统计）。
---

### 4. CompletableFuture 异步编排 + 文档导出策略模式

文档详情页通过 **CompletableFuture + 自定义线程池**异步编排并行聚合多源数据，响应时间由 350ms 降至 120ms；文档导出支持 PDF、Word、Markdown 等多种格式，使用 **策略模式 + 工厂模式**封装独立策略类，新增导出格式无需改动业务代码。

```java
// 并行聚合多源数据
CompletableFuture<Document> docFuture = CompletableFuture.supplyAsync(() -> getDocument(docId), executor);
CompletableFuture<User> authorFuture = docFuture.thenApplyAsync(doc -> getAuthor(doc.getOwnerId()), executor);
CompletableFuture<List<Permission>> permFuture = CompletableFuture.supplyAsync(() -> getPermissions(docId), executor);
CompletableFuture<Integer> viewFuture = CompletableFuture.supplyAsync(() -> getViewCount(docId), executor);
CompletableFuture.allOf(docFuture, authorFuture, permFuture, viewFuture).join();
```

**导出策略类结构：**
```
DocumentExportStrategy (接口)
    ├── PdfExportStrategy      (PDFBox)
    ├── WordExportStrategy     (Apache POI)
    └── MarkdownExportStrategy (CommonMark)

DocumentExportFactory (策略工厂)
```

---

### 5. Elasticsearch + IK 中文分词 替代 MySQL LIKE

原基于 MySQL LIKE 模糊搜索在数据量超 10 万条时耗时超 1.5s，引入 **Elasticsearch 并配置 IK 中文分词器**，将搜索响应降至 60ms，支持按相关度排序和高亮显示。

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 索引时分词器 | ik_max_word | 最细粒度分词，提高召回率 |
| 查询时分词器 | ik_smart | 智能分词，提高精确率 |
| 多字段加权 | title^3 + content^1 | 标题权重高于内容 |
| 结果高亮 | em 标签 | 匹配关键词高亮显示 |

---

### 6. ot.js + WebSocket 协同编辑

多人同时编辑同一文档时存在操作冲突，基于开源库 **ot.js + WebSocket** 实现协同编辑：前端将文本变更转为 retain/insert/delete 原子操作，后端负责操作广播与版本号管理，通过 transform 机制自动解决并发冲突，支持多人实时可见他人光标与内容变更。

| 功能 | 实现 |
|------|------|
| WebSocket 端点 | ws://host:port/ws/doc/{docId}?token=JWT_TOKEN |
| OT 算法 | 支持 retain/insert/delete 原子操作，transform 解决并发冲突 |
| 光标同步 | 实时显示其他用户光标位置（不同颜色区分） |
| 版本管理 | 自动递增文档版本号，配合乐观锁使用 |

---

## 快速开始

### 前置要求
- Docker & Docker Compose
- JDK 17+ (本地开发)
- Python 3.11+ (本地开发)
- Maven 3.9+

### 一键启动所有服务

```bash
# 克隆项目
git clone <repository-url>
cd knowledge

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

### 服务访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| Java 后端 | http://localhost:8080 | Spring Boot API |
| Python AI 服务 | http://localhost:8000 | FastAPI 文档: /docs |
| Qdrant 控制台 | http://localhost:6333/dashboard | 向量数据库管理 |
| Kibana | http://localhost:5601 | ES 可视化管理 |
| MinIO 控制台 | http://localhost:9001 | 默认账号: minioadmin/minioadmin |
| Swagger UI | http://localhost:8080/doc.html | API 文档 |
| WebSocket | ws://localhost:8080/ws/doc/{docId} | 实时协作编辑 |

---

## 目录结构

```
knowledge/
├── backend-java/                     # Java 后端服务 (Spring Boot)
│   ├── src/main/java/com/example/rag/
│   │   ├── client/                   # AI 服务客户端
│   │   │   ├── AiServiceClient.java        # RestTemplate 实现
│   │   │   └── AiServiceClientConfig.java  # RestTemplate 配置
│   │   ├── config/                   # 配置类 (ES, 线程池, MyBatis-Plus, WebSocket)
│   │   ├── controller/               # REST 控制器
│   │   ├── service/                  # 业务服务层
│   │   │   ├── export/               # 文档导出策略 (策略+工厂模式)
│   │   │   └── impl/                 # 服务实现
│   │   └── websocket/                # WebSocket 实时协作 (OT 算法)
│   └── src/main/resources/
│       ├── application.yml           # 主配置
│       └── db/init.sql               # 数据库初始化脚本
│
├── ai-service-python/                # Python AI 服务 (FastAPI)
│   ├── app/
│   │   ├── api/routes.py             # API 路由 (解析/检索/问答/评测)
│   │   ├── core/config.py            # 配置 (Qdrant/ES/Redis/LLM)
│   │   ├── schemas/                  # 请求/响应模型
│   │   ├── services/
│   │   │   ├── langchain_rag_service.py  # RAG 服务 (父子切块/增量重建/父块回溯)
│   │   │   ├── vector_store.py            # Qdrant 向量存储 (权限过滤)
│   │   │   ├── es_bm25.py                 # ES BM25 索引 (IK 中文分词)
│   │   │   ├── metadata_extractor.py       # LLM 元数据提取
│   │   │   ├── query_rewrite.py           # LLM query 改写
│   │   │   ├── retriever.py               # 双路召回 + RRF 融合
│   │   │   ├── evaluator.py               # RAGAS + Langfuse 评测
│   │   │   ├── chunker.py                 # 文本切块
│   │   │   ├── embedder.py                # Embedding 模型
│   │   │   ├── llm_client.py              # LLM 客户端
│   │   │   ├── parser.py                  # 文档解析
│   │   │   └── prompt_builder.py          # Prompt 构建
│   │   └── utils/                    # 工具类
│   ├── main.py                       # FastAPI 入口
│   └── requirements.txt              # Python 依赖
│
├── elasticsearch/                    # ES 配置 (IK 分词器插件)
├── docker-compose.yml                # Docker Compose 配置
└── README.md                         # 项目说明
```

---

## API 文档

启动服务后访问:
- Java 后端 Swagger UI: http://localhost:8080/doc.html
- Python AI 服务 Swagger UI: http://localhost:8000/docs

### Python AI 服务核心接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /ai/documents/parse | POST | 文档解析（父子切块 + LLM 元数据 + Qdrant + ES BM25） |
| /ai/documents/rebuild | POST | 增量重建（版本号定位，缓解全量重构 token 消耗） |
| /ai/documents/vectors/delete | POST | 删除文档索引（Qdrant + Redis + ES BM25） |
| /ai/search | POST | 混合检索（ES BM25 + Qdrant → RRF → 权限过滤） |
| /ai/chat | POST | RAG 智能问答 |
| /ai/chat/stream | POST | RAG 问答（SSE 流式输出 + 来源标注） |
| /ai/evaluate | POST | RAG 评测（RAGAS: faithfulness / answer relevancy / context precision） |

---

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| MYSQL_HOST | localhost | MySQL 主机 |
| MYSQL_PORT | 3306 | MySQL 端口 |
| MYSQL_PASSWORD | js2003 | MySQL 密码 |
| REDIS_HOST | localhost | Redis 主机 |
| REDIS_PORT | 6379 | Redis 端口 |
| ES_URIS | http://localhost:9200 | Elasticsearch 地址 |
| QDRANT_HOST | localhost | Qdrant 主机 |
| QDRANT_PORT | 6333 | Qdrant REST 端口 |
| QDRANT_GRPC_PORT | 6334 | Qdrant gRPC 端口 |
| MINIO_ENDPOINT | http://localhost:9000 | MinIO 地址 |
| LLM_API_KEY | - | LLM API Key |
| LLM_BASE_URL | https://api.deepseek.com/v1 | LLM API 地址 |
| LLM_MODEL_NAME | deepseek-chat | LLM 模型名称 |
| JWT_SECRET | - | JWT 签名密钥 |

---

## License

MIT License