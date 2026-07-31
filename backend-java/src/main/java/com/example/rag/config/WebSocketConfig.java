package com.example.rag.config;

import com.example.rag.websocket.DocumentEditWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 - 实时协作编辑
 *
 * <p>支持多人同时编辑同一文档，实现类语雀风格的实时协作功能。</p>
 *
 * <h3>WebSocket 端点: /ws/doc/{docId}</h3>
 *
 * <h3>消息协议 (JSON):</h3>
 * <pre>
 *   // 客户端 -> 服务器
 *   {
 *     "type": "operation",       // operation / cursor / sync
 *     "documentId": 123,
 *     "version": 5,
 *     "operation": {
 *       "type": "insert",        // insert / delete / retain
 *       "position": 100,
 *       "content": "新增文本",
 *       "length": 10             // delete 时使用
 *     }
 *   }
 *
 *   // 服务器 -> 客户端
 *   {
 *     "type": "operation",
 *     "documentId": 123,
 *     "version": 6,
 *     "userId": 456,
 *     "username": "张三",
 *     "operation": { ... },
 *     "timestamp": 1690000000000
 *   }
 * </pre>
 *
 * <h3>OT 算法 (Operational Transformation):</h3>
 * <p>使用 OT 算法处理并发编辑冲突，确保多用户编辑的一致性。</p>
 *
 * @author knowledge-rag-team
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DocumentEditWebSocketHandler documentEditWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(documentEditWebSocketHandler, "/ws/doc/{docId}")
                .setAllowedOriginPatterns("*");
    }
}
