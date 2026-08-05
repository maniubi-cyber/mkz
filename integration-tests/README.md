# 协同编辑端到端联调（WebSocket 实时同步 + 落库）

本目录的 `ws-collab-e2e.mjs` 在 **真实依赖栈**（docker-compose 起的 MySQL/Redis/backend-java）
上验证「成果 6：ot.js + WebSocket 协同编辑」的端到端闭环：

1. WS 实时同步（OT transform + 广播）
2. 自身 ack 机制（前端按 userId 识别自身操作，仅推进 revision）
3. 协同正文经 `PUT /content` 落库（last-write-wins，绕过 `@Version`）
4. 重开 `GET /detail` 读到新正文，浏览量自增，版本历史落库

脚本使用 Node >= 22 内置的全局 `WebSocket` / `fetch`，**无需 `npm install`**。

## 1. 启动依赖栈

```bash
cd <repo-root>
docker compose up -d            # 含 mysql/redis/elasticsearch/qdrant/minio/ai-service/backend-java
# 等待 backend-java 健康（首次构建较慢）
docker compose ps              # 确认 rag-backend-java 状态为 healthy/running
curl -s http://localhost:8080/actuator/health || echo "等待后端就绪..."
```

> 后端映射端口 `8080`（见 docker-compose.yml `backend-java.ports`）。

## 2. 准备两个可查看该文档的用户

`checkDocViewPermission` 规则：ADMIN 可见全部；否则仅 owner / `PUBLIC` / 同组织。
两位用户必须是 **不同 userId**（否则自身 ack 会被误判）。二选一：

- **方案 A（推荐）**：把两个测试账号都提升为 ADMIN
  ```sql
  UPDATE user SET role='ADMIN' WHERE username IN ('collab_a','collab_b');
  ```
- **方案 B**：用一个 `visibility='PUBLIC'` 的文档（owner 是 A，B 也能看）。

若数据库里还没有用户，脚本会先尝试 `POST /api/auth/login`，登录失败再自动
`POST /api/auth/register` 创建。所以直接给 `USER_A/PASS_A/USER_B/PASS_B` 即可。

## 3. 运行联调

```bash
cd integration-tests
export BASE_URL=http://localhost:8080
export DOC_ID=<一个两位用户都可查看的文档ID>
export USER_A=collab_a  PASS_A=collab_a_pass
export USER_B=collab_b  PASS_B=collab_b_pass
# 可选：直接给 JWT 跳过登录
# export TOKEN_A=xxx TOKEN_B=yyy

node ws-collab-e2e.mjs
```

退出码 `0` = 全部断言通过；`1` = 有失败或环境不可达。

## 4. 断言清单

| 断言 | 验证点 |
|------|--------|
| GET /detail 可读 | 用户 A 有权查看该文档 |
| 两位用户 WS 均连接成功 | JWT 鉴权 + 会话加入 |
| OT 收敛：两端文本一致 | 服务端 OT transform + 客户端 apply 正确 |
| 合并结果含 Hello 与空格 | 并发插入正确合并（顺序由 userId tie-break 决定） |
| A 收到 B 的远端操作 / B 收到 A 的远端操作 | 广播（excludeSender=false）生效 |
| 两端均收到自身 ack | 自身 ack 推进 revision 不重复改文本 |
| PUT /content 保存成功 | last-write-wins 落库（绕过 @Version） |
| 重开详情读到落库正文 | 保存即检查点，重开一致 |
| 浏览量自增（HINCRBY） | detail 接口访问即计数 |
| 版本历史落库（协同编辑保存） | document_version_history 写入 |

## 5. 已知限制与修复记录（与前端一致）

- **2026-08-01 客户端收敛 bug 修复**：`useCollabEditor.receiveOperation` 远端分支旧实现只
  transform 本地 pending、却把【未变换的 incoming】直接应用到文本，导致并发编辑（同位置插入、
  插入落入他人删除区间等）两端文本分叉。已改为把 incoming 依次与本地 pending transform 后
  再应用（ot.js 一致做法）。离线回归 `ot-client-convergence.test.mjs` 覆盖 5 个场景：
  有序协作 / 并发同位置插入（siteId 两种顺序）/ 插入落入并发删除 / 并发重叠删除，全部收敛。
- 单区间删除模型：同位置并发 INSERT 用 `userId` 做确定性 tie-break（绝不按函数参数顺序）；
  INSERT 落在他人 DELETE 区间内会被吞掉（字数丢失，但两端一致，不会分叉）。彻底修复需
  ot.js 式操作序列模型 + 前端协议改动。
- 协同保存用 `UpdateWrapper` 直接 `set content/last_editor_id/update_time`，**刻意绕过实体 `@Version`**，
  否则两人同时保存因 version 不匹配互抛 `OptimisticLockerException` 丢保存。OT 已保证各端文本收敛，保存只是检查点。
