package com.example.rag.websocket;

import com.example.rag.entity.Document;
import com.example.rag.mapper.DocumentMapper;
import com.example.rag.security.JwtTokenProvider;
import com.example.rag.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档编辑 WebSocket 处理器 - 实时协作编辑 + 完整 OT 算法
 *
 * <p>处理文档编辑的 WebSocket 连接，支持:</p>
 * <ul>
 *   <li>用户加入/离开编辑会话</li>
 *   <li>实时编辑操作同步（OT 算法）</li>
 *   <li>光标位置同步</li>
 *   <li>文档版本管理</li>
 * </ul>
 *
 * <h3>OT Transform 流程:</h3>
 * <ol>
 *   <li>客户端发送操作 (op_client, version_client)</li>
 *   <li>服务器获取当前文档版本 (version_server)</li>
 *   <li>若版本相同 → 直接广播</li>
 *   <li>若版本不同 → 获取服务器在 version_client 到 version_server 之间的所有操作历史</li>
 *   <li>将 op_client 与每个历史操作依次 transform，得到 op_transformed</li>
 *   <li>广播 op_transformed 给所有其他客户端</li>
 * </ol>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentEditWebSocketHandler extends TextWebSocketHandler {

    private final DocumentEditSessionManager sessionManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final OTEngine otEngine;
    private final DocumentMapper documentMapper;
    private final PermissionService permissionService;

    /** 会话属性: 用户 ID */
    private static final String ATTR_USER_ID = "userId";
    /** 会话属性: 用户名 */
    private static final String ATTR_USERNAME = "username";
    /** 会话属性: 文档 ID */
    private static final String ATTR_DOC_ID = "docId";

    /** 每个文档保留的最大操作历史条数 */
    private static final int MAX_HISTORY_SIZE = 100;

    /**
     * 文档ID -> 操作历史队列（用于OT transform）
     * key: docId, value: 按版本号排序的操作列表
     */
    private final Map<Long, List<OTOperationRecord>> documentOperationHistory =
            new ConcurrentHashMap<>();

    /**
     * 文档ID -> 当前历史中最早的操作版本号。
     *
     * <p>历史被截断（超过 MAX_HISTORY_SIZE）后，早于该版本号的客户端
     * 无法被完整 transform，必须强制其全量同步（发送 sync_response 后重载）。</p>
     */
    private final Map<Long, Integer> historyMinVersion = new ConcurrentHashMap<>();

    /**
     * 文档ID -> 文档级锁对象。
     *
     * <p>串行化同一文档的「读取版本 → OT transform → 递增版本 → 记录历史」
     * 关键路径。两个客户端并发发操作时，若不加锁，二者可能基于同一版本各自
     * transform 并递增，导致广播顺序与版本号错位、文本分叉。</p>
     */
    private final Map<Long, Object> documentLocks = new ConcurrentHashMap<>();

    private Object getDocLock(Long docId) {
        return documentLocks.computeIfAbsent(docId, k -> new Object());
    }

    /**
     * OT操作记录 - 用于版本历史追踪
     */
    private static class OTOperationRecord {
        final int version;
        final OTEngine.Operation op;

        OTOperationRecord(int version, OTEngine.Operation op) {
            this.version = version;
            this.op = op;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getParameterValue(session, "token");
        String docIdStr = getParameterValue(session, "docId");

        if (token == null || docIdStr == null) {
            session.close(new CloseStatus(4400, "缺少必要参数: token 或 docId"));
            return;
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                session.close(new CloseStatus(1003, "无效的认证令牌"));
                return;
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String username = jwtTokenProvider.getUsernameFromToken(token);
            Long docId = Long.parseLong(docIdStr);

            // ===== 权限校验：仅 WRITE 权限可加入协同编辑会话 =====
            // owner / admin / document_permission 中 WRITE、ADMIN 用户可编辑；
            // 只读用户直接拒绝握手，避免建立连接后被动的广播越权。
            Document doc = documentMapper.selectById(docId);
            if (doc == null) {
                session.close(new CloseStatus(4404, "文档不存在"));
                return;
            }
            try {
                permissionService.checkDocWritePermission(doc);
            } catch (Exception e) {
                log.warn("WebSocket 编辑权限被拒绝: docId={}, userId={}, reason={}",
                        docId, userId, e.getMessage());
                session.close(new CloseStatus(4403, "无权编辑该文档，仅文档所有者或被授权者可编辑"));
                return;
            }

            session.getAttributes().put(ATTR_USER_ID, userId);
            session.getAttributes().put(ATTR_USERNAME, username);
            session.getAttributes().put(ATTR_DOC_ID, docId);

            sessionManager.addSession(docId, session, userId, username);
            WebSocketSessionRegistry.register(session);

            log.info("WebSocket 连接建立: docId={}, userId={}, username={}, sessionId={}",
                    docId, userId, username, session.getId());

            broadcastUserJoin(session, docId, userId, username);

        } catch (NumberFormatException e) {
            session.close(new CloseStatus(4400, "无效的文档 ID"));
        } catch (Exception e) {
            log.error("WebSocket 连接建立失败", e);
            session.close(new CloseStatus(1011, "连接建立失败"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);

            Long docId = (Long) session.getAttributes().get(ATTR_DOC_ID);
            Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
            String username = (String) session.getAttributes().get(ATTR_USERNAME);

            if (docId == null) {
                sendError(session, "未找到文档 ID");
                return;
            }

            wsMessage.setDocumentId(docId);
            wsMessage.setUserId(userId);
            wsMessage.setUsername(username);
            wsMessage.setTimestamp(System.currentTimeMillis());

            switch (wsMessage.getType()) {
                case "operation":
                    handleOperation(wsMessage, session);
                    break;
                case "cursor":
                    handleCursor(wsMessage, session);
                    break;
                case "sync":
                    handleSync(wsMessage, session);
                    break;
                default:
                    sendError(session, "未知的消息类型: " + wsMessage.getType());
            }

        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long docId = (Long) session.getAttributes().get(ATTR_DOC_ID);
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        String username = (String) session.getAttributes().get(ATTR_USERNAME);

        if (docId != null) {
            sessionManager.removeSession(session.getId());
            WebSocketSessionRegistry.remove(session.getId());
            broadcastUserLeave(docId, userId, username);
        }

        log.info("WebSocket 连接关闭: docId={}, userId={}, sessionId={}, status={}",
                docId, userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误: sessionId={}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // ==================== OT 核心处理 ====================

    /**
     * 处理编辑操作 - 完整 OT transform 逻辑
     *
     * <p>核心流程：</p>
     * <ol>
     *   <li>解析客户端发来的操作</li>
     *   <li>获取客户端声明的版本号</li>
     *   <li>获取服务器当前版本号</li>
     *   <li>若版本不同，获取中间操作历史，依次 transform</li>
     *   <li>广播变换后的操作给所有其他客户端</li>
     *   <li>记录操作历史，递增版本号</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private void handleOperation(WebSocketMessage message, WebSocketSession session) throws Exception {
        Long docId = message.getDocumentId();

        // 解析操作
        Map<String, Object> opMap = (Map<String, Object>) message.getOperation();
        if (opMap == null) {
            sendError(session, "操作内容为空");
            return;
        }

        // 构建 OT 操作对象。siteId 取用户 ID：
        // 两个 INSERT 落在同一位置时，OT 引擎依靠它做确定性排序，避免文档分叉。
        Long opUserId = message.getUserId();
        OTEngine.Operation op = parseOTOperation(opMap, opUserId == null ? 0L : opUserId);
        if (op == null) {
            sendError(session, "操作解析失败");
            return;
        }

        // 获取客户端版本（客户端发送时携带的版本号）
        int clientVersion = message.getVersion() != null ? message.getVersion() : 0;

        // ===== 文档级锁：串行化 OT 关键路径 =====
        // 读取版本 → transform → 递增版本 → 记录历史 必须原子完成，
        // 否则并发操作会基于同一版本各自 transform 导致文本分叉。
        synchronized (getDocLock(docId)) {
            // 获取服务器当前版本
            int serverVersion = sessionManager.getVersion(docId);

            log.info("收到编辑操作: docId={}, clientVersion={}, serverVersion={}, op={}",
                    docId, clientVersion, serverVersion, op);

            // 如果客户端版本落后于服务器版本，需要进行 OT transform
            if (clientVersion < serverVersion) {
                OTEngine.Operation originalOp = op;

                // 历史截断降级：客户端版本早于历史中最老的操作 → 无法完整 transform，
                // 直接通知客户端全量重新同步（前端收到 sync_response 后重载最新正文）。
                Integer minVersion = historyMinVersion.get(docId);
                if (minVersion != null && clientVersion < minVersion) {
                    log.warn("编辑历史已截断，强制客户端全量同步: docId={}, clientVersion={}, minVersion={}",
                            docId, clientVersion, minVersion);
                    sendError(session, "编辑历史已过期，正在重新同步最新内容，请稍后继续编辑");
                    handleSync(message, session);
                    return;
                }

                // 获取从 clientVersion 到 serverVersion 之间的所有操作
                List<OTOperationRecord> history = getOperationHistory(docId);

                // 依次与历史操作进行 transform
                OTEngine.Operation transformedOp = op;
                for (OTOperationRecord record : history) {
                    if (record.version > clientVersion && record.version <= serverVersion) {
                        OTEngine.Operation before = transformedOp;
                        transformedOp = otEngine.transformOp(transformedOp, record.op);
                        log.debug("OT transform: {} against {} → {}", before, record.op, transformedOp);
                    }
                }

                op = transformedOp;
                log.info("OT transform 完成: original={}, transformed={}", originalOp, op);
            }

            // 递增版本并广播
            int newVersion = sessionManager.incrementAndGetVersion(docId);
            message.setVersion(newVersion);

            // 将变换后的操作广播给其他用户
            try {
                // 更新消息中的操作为变换后的版本
                message.setOperation(objectMapper.convertValue(
                        serializeOTOperation(op), WebSocketMessage.Operation.class));

                String messageJson = objectMapper.writeValueAsString(message);
                // 不排除发送者：前端需要收到自己操作的 ack 来推进 revision（否则 OT 客户端
                // 永远收不到自身版本号，并发编辑时会与服务器历史双重 transform 导致文本分叉）。
                // 前端按 userId 识别自身操作，仅推进版本、不重复应用到文本。
                sessionManager.broadcastToDocument(docId, messageJson, false, session.getId());
            } catch (Exception e) {
                log.error("广播编辑操作失败", e);
            }

            // 记录操作历史
            addOperationHistory(docId, newVersion, op);

            log.info("操作已广播: docId={}, newVersion={}, op={}", docId, newVersion, op);
        }
    }

    /**
     * 将客户端 JSON 操作解析为 OTEngine.Operation
     */
    private OTEngine.Operation parseOTOperation(Map<String, Object> opMap, long siteId) {
        try {
            String type = (String) opMap.get("type");
            // Jackson 把 JSON 数字反序列化成 Object 时，可能是 Integer 也可能是 Long，
            // 统一按 Number 取值再转 int，避免 ClassCastException。
            int position = intValue(opMap.get("position"), 0);
            String content = (String) opMap.get("content");
            int length = intValue(opMap.get("length"), 0);
            int retain = intValue(opMap.get("retain"), 0);

            if ("insert".equals(type)) {
                return new OTEngine.Operation(
                        OTEngine.OpType.INSERT,
                        position,
                        content != null ? content : "",
                        0, 0, siteId
                );
            } else if ("delete".equals(type)) {
                return new OTEngine.Operation(
                        OTEngine.OpType.DELETE,
                        position, "", length, 0, siteId
                );
            } else if ("retain".equals(type)) {
                return new OTEngine.Operation(
                        OTEngine.OpType.RETAIN,
                        position, "", 0, retain, siteId
                );
            }
            log.warn("未知的操作类型: {}", type);
        } catch (Exception e) {
            log.error("解析操作失败: {}", opMap, e);
        }
        return null;
    }

    /** 安全地把任意数字对象转为 int */
    private static int intValue(Object value, int defaultValue) {
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    /**
     * 将 OTEngine.Operation 序列化为客户端 JSON
     */
    private Map<String, Object> serializeOTOperation(OTEngine.Operation op) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", op.getType().toString().toLowerCase());
        map.put("position", op.getPos());

        if (op.getType() == OTEngine.OpType.INSERT) {
            map.put("content", op.getText());
        } else if (op.getType() == OTEngine.OpType.DELETE) {
            map.put("length", op.getLength());
        } else if (op.getType() == OTEngine.OpType.RETAIN) {
            map.put("retain", op.getCount());
        }

        return map;
    }

    /**
     * 获取文档的操作历史（返回快照，避免遍历时被并发写入干扰）
     */
    private List<OTOperationRecord> getOperationHistory(Long docId) {
        List<OTOperationRecord> history = documentOperationHistory.get(docId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /**
     * 添加操作历史记录
     */
    private void addOperationHistory(Long docId, int version, OTEngine.Operation op) {
        List<OTOperationRecord> history =
                documentOperationHistory.computeIfAbsent(docId, k -> new ArrayList<>());

        synchronized (history) {
            history.add(new OTOperationRecord(version, op));

            // 限制历史记录长度，避免无限增长（保留最近 MAX_HISTORY_SIZE 条）
            // 注意：subList 返回的是原列表视图，必须拷贝成独立列表后再替换，
            // 否则后续 add 会因视图与源列表结构性修改而抛 ConcurrentModificationException。
            if (history.size() > MAX_HISTORY_SIZE) {
                List<OTOperationRecord> trimmed =
                        new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE, history.size()));
                documentOperationHistory.put(docId, trimmed);
                // 记录截断后历史中最老的版本号：早于它的客户端无法被完整 transform，
                // 由 handleOperation 触发全量同步降级。
                historyMinVersion.put(docId, trimmed.get(0).version);
                log.warn("OT 历史已截断: docId={}, 保留最近 {} 条（minVersion={}）",
                        docId, trimmed.size(), trimmed.get(0).version);
            }
        }
    }

    // ==================== 其他消息处理 ====================

    private void handleCursor(WebSocketMessage message, WebSocketSession session) {
        Long docId = message.getDocumentId();
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            sessionManager.broadcastToDocument(docId, messageJson, true, session.getId());
        } catch (Exception e) {
            log.error("广播光标位置失败", e);
        }
    }

    private void handleSync(WebSocketMessage message, WebSocketSession session) {
        Long docId = message.getDocumentId();
        int version = sessionManager.getVersion(docId);

        WebSocketMessage syncResponse = WebSocketMessage.builder()
                .type("sync_response")
                .documentId(docId)
                .version(version)
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            String messageJson = objectMapper.writeValueAsString(syncResponse);
            session.sendMessage(new TextMessage(messageJson));
        } catch (Exception e) {
            log.error("发送同步响应失败", e);
        }
    }

    private void broadcastUserJoin(WebSocketSession session, Long docId, Long userId, String username) {
        try {
            WebSocketMessage joinMessage = WebSocketMessage.builder()
                    .type("join")
                    .documentId(docId)
                    .userId(userId)
                    .username(username)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String messageJson = objectMapper.writeValueAsString(joinMessage);
            sessionManager.broadcastToDocument(docId, messageJson, true, session.getId());
            sendOnlineUsers(session, docId);

        } catch (Exception e) {
            log.error("广播用户加入消息失败", e);
        }
    }

    private void broadcastUserLeave(Long docId, Long userId, String username) {
        try {
            WebSocketMessage leaveMessage = WebSocketMessage.builder()
                    .type("leave")
                    .documentId(docId)
                    .userId(userId)
                    .username(username)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String messageJson = objectMapper.writeValueAsString(leaveMessage);
            sessionManager.broadcastToDocument(docId, messageJson, false, null);

        } catch (Exception e) {
            log.error("广播用户离开消息失败", e);
        }
    }

    private void sendOnlineUsers(WebSocketSession session, Long docId) {
        try {
            var onlineUsers = sessionManager.getOnlineUsers(docId);
            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "online_users",
                    "documentId", docId,
                    "users", onlineUsers,
                    "timestamp", System.currentTimeMillis()
            ));
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(messageJson));
            }
        } catch (Exception e) {
            log.error("发送在线用户列表失败", e);
        }
    }

    private void sendError(WebSocketSession session, String error) {
        try {
            if (session.isOpen()) {
                WebSocketMessage errorMessage = WebSocketMessage.builder()
                        .type("error")
                        .error(error)
                        .timestamp(System.currentTimeMillis())
                        .build();
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
            }
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    private String getParameterValue(WebSocketSession session, String paramName) {
        String uri = session.getUri().toString();
        if (uri.contains(paramName + "=")) {
            String[] params = uri.split(paramName + "=");
            if (params.length > 1) {
                String value = params[1];
                if (value.contains("&")) {
                    value = value.substring(0, value.indexOf("&"));
                }
                return value;
            }
        }
        return null;
    }
}
