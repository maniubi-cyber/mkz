# 知识库文档协作平台（RAG 智能问答系统）

## 项目简介

为解决团队内部文档分散、搜索效率低、知识复用难等问题，独立设计并开发一套类语雀风格的文档协作平台。在传统文档管理基础上，通过**跨语言微服务架构**引入 RAG（检索增强生成）能力，实现基于文档内容的 AI 智能问答，让知识获取从"关键词搜索"升级为"自然语言问答"。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Cloud Alibaba | 2023.0.1.0 | 微服务框架（Nacos 注册配置中心 + Sentinel 熔断限流） |
| Spring Boot | 3.3.0 | Java 后端框架 |
| Spring Cloud OpenFeign | - | 声明式 HTTP 客户端（服务间调用） |
| Elasticsearch | 8.13.0 | 全文检索（IK 中文分词 + 高亮） |
| Redis | 7 | 缓存 + 子切块存储 |
| FastAPI | 0.111+ | Python AI 服务框架 |
| LangChain | 0.2+ | RAG 框架 |
| MySQL | 8.0 | 文档元数据存储（MyBatis-Plus + 乐观锁） |
| Qdrant | 1.7+ | 向量数据库（父切块存储） |
| WebSocket | - | 实时协作编辑（OT 算法 + 光标同步） |
| MinIO | - | 文件对象存储 |
| Docker Compose | - | 一键部署所有基础设施 |

---

## 项目架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue/React)                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Java 后端服务 (Spring Boot 3.3)                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Controller 层                         │   │
│  │  DocumentController | AuthController | KnowledgeBaseCtrl │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Service 层                            │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐           │   │
│  │  │ 异步编排   │ │ ES 搜索    │ │ 文档导出   │           │   │
│  │  │ Completabl │ │ IK 分词    │ │ 策略+工厂  │           │   │
│  │  │ eFuture    │ │ 高亮显示   │ │ 模式       │           │   │
│  │  └────────────┘ └────────────┘ └────────────┘           │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    数据访问层                            │   │
│  │  MyBatis-Plus(@Version乐观锁) | Redis | RestTemplate→Feign│  │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐              ┌─────────────────────────────┐
│   MySQL + ES    │              │   Python AI 服务 (FastAPI)  │
│   Redis + MinIO │◄─OpenFeign──►│   LangChain RAG Pipeline    │
│                 │              │   Qdrant (向量存储)          │
└─────────────────┘              └─────────────────────────────┘
```

---

## 项目成果

### 1. 接口性能优化（CompletableFuture 异步编排）

文档详情页需聚合"文档内容 + 作者信息 + 权限列表 + 浏览数"等多源数据。最初串行调用耗时 350ms，通过 **CompletableFuture 异步编排 + 自定义线程池** 并行查询，将响应时间压至 120ms，性能提升约 **65%**。

```java
// 并行发起4个异步任务
CompletableFuture<Document> docFuture = CompletableFuture.supplyAsync(() -> getDocument(docId), executor);
CompletableFuture<User> authorFuture = docFuture.thenApplyAsync(doc -> getAuthor(doc.getOwnerId()), executor);
CompletableFuture<List<Permission>> permFuture = CompletableFuture.supplyAsync(() -> getPermissions(docId), executor);
CompletableFuture<Integer> viewCountFuture = CompletableFuture.supplyAsync(() -> getViewCount(docId), executor);

// 等待所有任务完成并聚合
CompletableFuture.allOf(docFuture, authorFuture, permFuture, viewCountFuture).join();
```

**技术要点：**
- 自定义线程池配置：核心线程 10，最大线程 20，队列容量 200
- 使用 `allOf` 聚合多个异步结果
- 避免使用默认 ForkJoinPool，防止线程资源耗尽

---

### 2. 全文检索重构（Elasticsearch + IK 分词）

原基于 MySQL LIKE '%keyword%' 的模糊搜索在数据量超 10 万条时耗时超 1.5s，引入 **Elasticsearch 并配置 IK 中文分词器**，将搜索响应降至 60ms，并支持按相关度排序和高亮显示。

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 索引时分词器 | `ik_max_word` | 最细粒度分词，提高召回率 |
| 查询时分词器 | `ik_smart` | 智能分词，提高精确率 |
| 多字段加权 | `title^3 + content^1` | 标题权重高于内容 |
| 结果高亮 | `<em>` 标签 | 匹配关键词高亮显示 |

---

### 3. 设计模式消除臃肿代码（策略 + 工厂）

文档导出支持 PDF、Word、Markdown 三种格式，最初用大量 if-else 判断。使用 **策略模式 + 工厂模式** 重构，将每种导出逻辑封装为独立策略类，新增格式只需添加类，代码复杂度显著降低，可拓展性增强。

```java
// 重构后：一行代码搞定
DocumentExportStrategy strategy = DocumentExportStrategyFactory.getStrategy(format);
return strategy.export(document);
```

**类结构：**
```
DocumentExportStrategy (接口)
    ├── PdfExportStrategy      (PDFBox)
    ├── WordExportStrategy     (Apache POI)
    └── MarkdownExportStrategy (CommonMark)

DocumentExportFactory (策略工厂)
```

---

### 4. 乐观锁解决并发编辑冲突

多人同时编辑同一文档时，后提交者可能覆盖先提交者的内容。在文档表中引入 **version 版本号字段**，更新时对比 version 字段解决冲突问题，同时利用 MyBatis-Plus 的 `@Version` 注解实现无侵入式乐观锁控制。

```java
@Version
private Integer version;

// 自动生成的 SQL:
// UPDATE document SET ..., version = version + 1 WHERE id = ? AND version = ?
// 如果受影响行数为0，抛出 OptimisticLockerException
```

---

### 5. 微服务间通信优化（OpenFeign 替代 RestTemplate）

在跨语言微服务架构中，Java 后端需调用 Python AI 服务。最初使用 `RestTemplate` 硬编码 URL，代码冗长且难以维护。引入 **Spring Cloud OpenFeign** 实现声明式服务调用，配合 Sentinel 实现熔断降级。

```java
// 之前（RestTemplate）
String url = aiServiceBaseUrl + "/api/parse";
Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);

// 现在（OpenFeign 声明式调用）
@FeignClient(name = "ai-service", url = "${ai-service.base-url}")
public interface AiServiceClient {
    @PostMapping("/api/parse")
    Map<String, Object> parseDocument(@RequestBody Map<String, Object> request);
}

// 调用方式：像调用本地方法一样
Map<String, Object> response = aiServiceClient.parseDocument(requestBody);
```

**OpenFeign 优势：**
- 声明式接口，代码简洁易读
- 自动集成 Nacos 服务发现和负载均衡
- 一行配置集成 Sentinel 熔断降级
- 支持请求/响应压缩，提升传输效率

---

### 6. RAG 智能问答（LangChain + Qdrant）

团队文档查询效率低，基于 **LangChain** 设计问答系统：

**父子切块策略（Parent-Child Chunking）：**
| 切块类型 | 大小 | 存储 | 用途 |
|----------|------|------|------|
| 父切块 | 1000字符 | Qdrant | 向量检索召回，保持完整段落结构 |
| 子切块 | 200字符 | Redis | 精确匹配和 LLM 输入，减少 token 消耗 |

**Qdrant 优势:**
- 轻量级部署，单二进制无需 etcd/MinIO 依赖
- 丰富的 payload 过滤条件
- 生产环境稳定，支持分布式扩展
- REST + gRPC 双协议，Python SDK 成熟

---

### 7. 实时协作编辑（WebSocket + OT 算法）

类语雀风格的多人实时协作编辑功能：

| 功能 | 实现 |
|------|------|
| WebSocket 端点 | `ws://host:port/ws/doc/{docId}?token=JWT_TOKEN` |
| OT 算法 | 支持 Insert/Delete/Retain 操作，确保多用户编辑一致性 |
| 光标同步 | 实时显示其他用户的光标位置（不同颜色区分） |
| 在线状态 | 显示当前文档的在线用户列表 |
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
cd knowledge-rag-system-main

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
| Nacos 控制台 | http://localhost:8848/nacos | 默认账号: nacos/nacos |
| Sentinel 控制台 | http://localhost:8081 | 默认账号: sentinel/sentinel |
| Kibana | http://localhost:5601 | ES 可视化管理 |
| MinIO 控制台 | http://localhost:9001 | 默认账号: minioadmin/minioadmin |
| Qdrant 控制台 | http://localhost:6333 | Qdrant REST API |
| Swagger UI | http://localhost:8080/doc.html | API 文档 |
| WebSocket | ws://localhost:8080/ws/doc/{docId} | 实时协作编辑 |

---

## 目录结构

```
knowledge-rag-system-main/
├── backend-java/                     # Java 后端服务 (Spring Boot)
│   ├── src/main/java/com/example/rag/
│   │   ├── client/                   # OpenFeign 客户端
│   │   │   ├── AiServiceClient.java      # AI 服务 Feign 接口
│   │   │   └── AiServiceClientConfig.java # Feign 配置
│   │   ├── common/                   # 通用工具类
│   │   ├── config/                   # 配置类 (ES, 线程池, MyBatis-Plus)
│   │   ├── controller/               # REST 控制器
│   │   ├── dto/                      # 数据传输对象
│   │   ├── entity/                   # 数据库实体
│   │   ├── mapper/                   # MyBatis Mapper
│   │   ├── security/                 # JWT 安全配置
│   │   ├── service/                  # 业务服务层
│   │   │   ├── export/               # 文档导出策略
│   │   │   │   ├── DocumentExportStrategy.java      # 策略接口
│   │   │   │   ├── DocumentExportFactory.java       # 策略工厂
│   │   │   │   ├── PdfExportStrategy.java           # PDF导出
│   │   │   │   ├── WordExportStrategy.java          # Word导出
│   │   │   │   └── MarkdownExportStrategy.java      # Markdown导出
│   │   │   └── impl/                 # 服务实现
│   │   └── websocket/                # WebSocket 实时协作
│   └── src/main/resources/
│       ├── application.yml           # 主配置
│       └── db/init.sql               # 数据库初始化脚本
│
├── ai-service-python/                # Python AI 服务 (FastAPI)
│   ├── app/
│   │   ├── api/                      # API 路由
│   │   ├── core/                     # 配置
│   │   ├── schemas/                  # 请求/响应模型
│   │   ├── services/
│   │   │   ├── langchain_rag_service.py  # LangChain RAG 服务
│   │   │   ├── vector_store.py       # Qdrant 向量存储
│   │   │   ├── embedder.py           # Embedding 模型
│   │   │   ├── llm_client.py         # LLM 客户端
│   │   │   ├── chunker.py            # 文本切块（父子切块）
│   │   │   ├── retriever.py          # 检索器
│   │   │   ├── parser.py             # 文档解析
│   │   │   └── prompt_builder.py     # Prompt 构建
│   │   └── utils/                    # 工具类
│   ├── main.py                       # FastAPI 入口
│   └── requirements.txt              # Python 依赖
│
├── docker-compose.yml                # Docker Compose 配置
└── README.md                         # 项目说明
```

---

## API 文档

启动服务后访问:
- Java 后端 Swagger UI: http://localhost:8080/doc.html
- Python AI 服务 Swagger UI: http://localhost:8000/docs

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
| NACOS_SERVER | localhost:8848 | Nacos 地址 |
| SENTINEL_DASHBOARD | localhost:8081 | Sentinel 控制台 |
| MINIO_ENDPOINT | http://localhost:9000 | MinIO 地址 |
| LLM_API_KEY | - | LLM API Key |
| LLM_BASE_URL | https://api.deepseek.com/v1 | LLM API 地址 |
| LLM_MODEL_NAME | deepseek-chat | LLM 模型名称 |
| JWT_SECRET | - | JWT 签名密钥 |

---

## License

MIT License
