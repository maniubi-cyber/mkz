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
 *   <li>documentVersions: 文档ID -> 当前版本号</li>
 * </ul>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class DocumentEditSessionManager {

    private final Map<Long, Set<SessionInfo>> documentSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionDocumentMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> documentVersions = new ConcurrentHashMap<>();

    private static final String[] USER_COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
            "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F",
            "#BB8FCE", "#85C1E9", "#F8B500", "#00CED1"
    };

    private int colorIndex = 0;

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

    public void broadcastToDocument(Long docId, String message, boolean excludeSender, String senderId) {
        Set<SessionInfo> sessions = documentSessions.get(docId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        for (SessionInfo sessionInfo : sessions) {
            if (excludeSender && sessionInfo.getSessionId().equals(senderId)) {
                continue;
            }

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

    public int getSessionCount(Long docId) {
        Set<SessionInfo> sessions = documentSessions.get(docId);
        return sessions != null ? sessions.size() : 0;
    }

    public List<SessionInfo> getOnlineUsers(Long docId) {
        Set<SessionInfo> sessions = documentSessions.get(docId);
        return sessions != null ? new ArrayList<>(sessions) : Collections.emptyList();
    }

    public synchronized int incrementAndGetVersion(Long docId) {
        return documentVersions.merge(docId, 1, Integer::sum);
    }

    public int getVersion(Long docId) {
        return documentVersions.getOrDefault(docId, 0);
    }

    public void setVersion(Long docId, int version) {
        documentVersions.put(docId, version);
    }

    private synchronized String getNextColor() {
        String color = USER_COLORS[colorIndex % USER_COLORS.length];
        colorIndex++;
        return color;
    }

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
