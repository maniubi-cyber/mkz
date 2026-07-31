package com.example.rag.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表
 *
 * <p>维护所有活跃的 WebSocket 会话，支持通过 sessionId 快速查找会话。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    /** sessionId -> WebSocketSession */
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 注册会话
     */
    public static void register(WebSocketSession session) {
        SESSIONS.put(session.getId(), session);
    }

    /**
     * 移除会话
     */
    public static void remove(String sessionId) {
        SESSIONS.remove(sessionId);
    }

    /**
     * 获取会话
     */
    public static WebSocketSession getSession(String sessionId) {
        return SESSIONS.get(sessionId);
    }

    /**
     * 获取所有活跃会话数量
     */
    public static int getActiveSessionCount() {
        return SESSIONS.size();
    }
}
