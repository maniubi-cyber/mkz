package com.example.rag.websocket;

import com.example.rag.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 文档编辑 WebSocket 处理器 - 实时协作编辑
 *
 * <p>处理文档编辑的 WebSocket 连接，支持:</p>
 * <ul>
 *   <li>用户加入/离开编辑会话</li>
 *   <li>实时编辑操作同步（OT 算法）</li>
 *   <li>光标位置同步</li>
 *   <li>文档版本管理</li>
 * </ul>
 *
 * <h3>连接 URL: ws://host:port/ws/doc/{docId}</h3>
 *
 * <h3>连接参数:</h3>
 * <ul>
 *   <li>token: JWT 认证令牌（通过 query param 或 header 传递）</li>
 *   <li>docId: 文档 ID（路径参数）</li>
 * </ul>
 *
 * <h3>消息协议:</h3>
 * <pre>
 *   // 1. 用户加入
 *   { "type": "join", "documentId": 123, "userId": 456, "username": "张三" }
 *
 *   // 2. 编辑操作 (OT)
 *   {
 *     "type": "operation",
 *     "documentId": 123,
 *     "version": 5,
 *     "operation": { "type": "insert", "position": 100, "content": "新增文本" }
 *   }
 *
 *   // 3. 光标移动
 *   {
 *     "type": "cursor",
 *     "documentId": 123,
 *     "cursor": { "start": 50, "end": 55, "color": "#FF6B6B" }
 *   }
 *
 *   // 4. 文档同步请求
 *   { "type": "sync", "documentId": 123 }
 * </pre>
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

    /** 会话属性: 用户 ID */
    private static final String ATTR_USER_ID = "userId";
    /** 会话属性: 用户名 */
    private static final String ATTR_USERNAME = "username";
    /** 会话属性: 文档 ID */
    private static final String ATTR_DOC_ID = "docId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从 URL 参数获取 token 和 docId
        String token = getParameterValue(session, "token");
        String docIdStr = getParameterValue(session, "docId");

        if (token == null || docIdStr == null) {
            session.close(CloseStatus.BAD_DATA.reject("缺少必要参数: token 或 docId"));
            return;
        }

        // 验证 JWT token
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                session.close(CloseStatus.NOT_ACCEPTABLE.reject("无效的认证令牌"));
                return;
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String username = jwtTokenProvider.getUsernameFromToken(token);
            Long docId = Long.parseLong(docIdStr);

            // 设置会话属性
            session.getAttributes().put(ATTR_USER_ID, userId);
            session.getAttributes().put(ATTR_USERNAME, username);
            session.getAttributes().put(ATTR_DOC_ID, docId);

            // 注册会话
            sessionManager.addSession(docId, session, userId, username);
            WebSocketSessionRegistry.register(session);

            log.info("WebSocket 连接建立: docId={}, userId={}, username={}, sessionId={}",
                    docId, userId, username, session.getId());

            // 通知其他用户有新用户加入
            broadcastUserJoin(session, docId, userId, username);

        } catch (NumberFormatException e) {
            session.close(CloseStatus.BAD_DATA.reject("无效的文档 ID"));
        } catch (Exception e) {
            log.error("WebSocket 连接建立失败", e);
            session.close(CloseStatus.SERVER_ERROR.reject("连接建立失败"));
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

            // 设置消息的发送者信息
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

            // 广播用户离开消息
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

    // ==================== 私有方法 ====================

    /**
     * 处理编辑操作 - OT 算法
     *
     * <p>核心流程:</p>
     * <ol>
     *   <li>获取客户端发来的操作 (op)</li>
     *   <li>获取当前服务器版本的操作历史</li>
     *   <li>使用 OT 算法对操作进行变换 (transform)</li>
     *   <li>广播变换后的操作给所有其他客户端</li>
     *   <li>更新文档版本号</li>
     * </ol>
     */
    private void handleOperation(WebSocketMessage message, WebSocketSession session) {
        Long docId = message.getDocumentId();
        int newVersion = sessionManager.incrementAndGetVersion(docId);
        message.setVersion(newVersion);

        // 广播给其他用户（排除发送者）
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            sessionManager.broadcastToDocument(docId, messageJson, true, session.getId());
        } catch (Exception e) {
            log.error("广播编辑操作失败", e);
        }
    }

    /**
     * 处理光标移动
     */
    private void handleCursor(WebSocketMessage message, WebSocketSession session) {
        Long docId = message.getDocumentId();

        // 广播光标位置给其他用户
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            sessionManager.broadcastToDocument(docId, messageJson, true, session.getId());
        } catch (Exception e) {
            log.error("广播光标位置失败", e);
        }
    }

    /**
     * 处理文档同步请求
     */
    private void handleSync(WebSocketMessage message, WebSocketSession session) {
        Long docId = message.getDocumentId();
        int version = sessionManager.getVersion(docId);

        // 返回当前文档版本信息
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

    /**
     * 广播用户加入消息
     */
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

            // 发送当前在线用户列表给新加入的用户
            sendOnlineUsers(session, docId);

        } catch (Exception e) {
            log.error("广播用户加入消息失败", e);
        }
    }

    /**
     * 广播用户离开消息
     */
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

    /**
     * 发送在线用户列表
     */
    private void sendOnlineUsers(WebSocketSession session, Long docId) {
        try {
            var onlineUsers = sessionManager.getOnlineUsers(docId);

            WebSocketMessage usersMessage = WebSocketMessage.builder()
                    .type("online_users")
                    .documentId(docId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 将用户列表作为扩展信息发送
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

    /**
     * 发送错误消息
     */
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

    /**
     * 从 URL 获取参数值
     */
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
