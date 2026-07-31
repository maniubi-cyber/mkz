package com.example.rag.websocket;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 文档编辑会话管理器
 *
 * <p>管理所有 WebSocket 会话，支持多用户同时编辑同一文档。</p>
 *
 * <h3>数据结构:</h3>
 * <ul>
 *   <li>documentSessions: 文档ID -> 该文档的所有活跃会话集合</li>
 *   <li>sessionDocumentMap: 会话ID -> 文档ID 的反向映射</li>
 *   <li>userColors: 用户ID -> 颜色（用于区分不同用户的光标）</li>
 * </ul>
 *
 * <h3>协作特性:</h3>
 * <ul>
 *   <li>多人同时编辑同一文档</li>
 *   <li>实时同步编辑操作</li>
 *   <li>光标位置实时显示</li>
 *   <li>用户在线状态管理</li>
 * </ul>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class DocumentEditSessionManager {

    /** 文档ID -> 该文档的所有活跃会话集合 */
    private final Map<Long, Set<SessionInfo>> documentSessions = new ConcurrentHashMap<>();

    /** 会话ID -> 文档ID 的反向映射 */
    private final Map<String, Long> sessionDocumentMap = new ConcurrentHashMap<>();

    /** 文档ID -> 当前文档版本号 */
    private final Map<Long, Integer> documentVersions = new ConcurrentHashMap<>();

    /** 用户颜色池（用于区分不同用户） */
    private static final String[] USER_COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
            "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F",
            "#BB8FCE", "#85C1E9", "#F8B500", "#00CED1"
    };

    private int colorIndex = 0;

    /**
     * 添加会话
     *
     * @param docId     文档 ID
     * @param session   WebSocket 会话
     * @param userId    用户 ID
     * @param username  用户名
     */
    public synchronized void addSession(Long docId, WebSocketSession session, Long userId, String username) {
        SessionInfo sessionInfo = SessionInfo.builder()
                .sessionId(session.getId())
                .userId(userId)
                .username(username)
                .color(getNextColor())
                .joinTime(System.currentTimeMillis())
                .build();

        documentSessions.computeIfAbsent(docId, k -> new CopyOnWriteArraySet<>()).add(sessionInfo);
        sessionDocumentMap.put(session.getId(), docId);

        log.info("用户加入文档编辑: docId={}, userId={}, username={}, 当前在线人数={}",
                docId, userId, username, getSessionCount(docId));
    }

    /**
     * 移除会话
     *
     * @param sessionId 会话 ID
     */
    public synchronized void removeSession(String sessionId) {
        Long docId = sessionDocumentMap.remove(sessionId);
        if (docId != null) {
            Set<SessionInfo> sessions = documentSessions.get(docId);
            if (sessions != null) {
                sessions.removeIf(s -> s.getSessionId().equals(sessionId));
                if (sessions.isEmpty()) {
                    documentSessions.remove(docId);
                }
            }
            log.info("用户离开文档编辑: docId={}, sessionId={}, 剩余在线人数={}",
                    docId, sessionId, getSessionCount(docId));
        }
    }

    /**
     * 广播消息给文档的所有其他会话
     *
     * @param docId         文档 ID
     * @param message       消息内容
     * @param excludeSender 是否排除发送者
     * @param senderId      发送者会话 ID
     */
    public void broadcastToDocument(Long docId, String message, boolean excludeSender, String senderId) {
        Set<SessionInfo> sessions = documentSessions.get(docId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        for (SessionInfo sessionInfo : sessions) {
            if (excludeSender && sessionInfo.getSessionId().equals(senderId)) {
                continue;
            }

            // 获取实际的 WebSocketSession 并发送消息
            WebSocketSession webSocketSession = WebSocketSessionRegistry.getSession(sessionInfo.getSessionId());
            if (webSocketSession != null && webSocketSession.isOpen()) {
                try {
                    synchronized (webSocketSession) {
                        webSocketSession.sendMessage(new org.springframework.web.socket.TextMessage(message));
                    }
                } catch (IOException e) {
                    log.warn("广播消息失败: docId={}, sessionId={}", docId, sessionInfo.getSessionId(), e);
                }
            }
        }
    }

    /**
     * 获取文档的在线用户数量
     */
    public int getSessionCount(Long docId) {
        Set<SessionInfo> sessions = documentSessions.get(docId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * 获取文档的在线用户列表
     */
    public List<SessionInfo> getOnlineUsers(Long docId) {
        Set<SessionInfo> sessions = documentSessions.get(docId);
        return sessions != null ? new ArrayList<>(sessions) : Collections.emptyList();
    }

    /**
     * 获取并递增文档版本号
     */
    public synchronized int incrementAndGetVersion(Long docId) {
        return documentVersions.merge(docId, 1, Integer::sum);
    }

    /**
     * 获取当前文档版本号
     */
    public int getVersion(Long docId) {
        return documentVersions.getOrDefault(docId, 0);
    }

    /**
     * 设置文档版本号
     */
    public void setVersion(Long docId, int version) {
        documentVersions.put(docId, version);
    }

    /**
     * 获取下一个用户颜色
     */
    private synchronized String getNextColor() {
        String color = USER_COLORS[colorIndex % USER_COLORS.length];
        colorIndex++;
        return color;
    }

    /**
     * 会话信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private Long userId;
        private String username;
        private String color;
        private Long joinTime;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SessionInfo that = (SessionInfo) o;
            return Objects.equals(sessionId, that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId);
        }
    }
}