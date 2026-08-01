package com.mkz.chat.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.chat.domain.po.ChatSession;
import com.mkz.chat.domain.query.RecordQuery;
import com.mkz.chat.feign.AiBridgeClient;
import com.mkz.chat.mapper.ChatSessionMapper;
import com.mkz.chat.service.IChatSessionService;
import com.mkz.common.domain.R;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.utils.UserContext;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * <p>
 * 聊天对话的每个片段记录（分片存储） 服务实现类。
 * </p>
 * <p>
 * 改造说明：本类已重构为薄网关层，AI 能力（记忆管理、ReAct、工具调用、HITL、RAG）
 * 全部通过 Feign 调用 mkz-ai-bridge 桥接服务，桥接服务再用 OpenFeign 调 Python AI 服务。
 * Java 侧仅负责：用户鉴权（依赖 UserContext）、SSE 流式转发、聊天记录落库。
 * </p>
 *
 * @author lusy
 * @since 2025-05-06
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    private final AiBridgeClient aiBridgeClient;

    /**
     * 非流式对话：通过 Feign 调桥接服务，从统一响应体 R 中取 answer。
     */
    @Override
    public String chat(String sessionId, String message) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        R<Map<String, Object>> r = aiBridgeClient.chat(message, sessionId, userId.toString(), currentJwtToken());
        if (r.success() && r.getData() != null) {
            Object answer = r.getData().get("answer");
            return answer == null ? "" : answer.toString();
        }
        return "";
    }

    @Override
    public PageDTO<ChatSession> getRecord(RecordQuery query) {
        Page<ChatSession> page = this.lambdaQuery()
                .eq(ChatSession::getSessionId, query.getSessionId())
                .eq(ChatSession::getUserId, UserContext.getUser())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        return PageDTO.of(page);
    }

    /**
     * 流式对话：通过 Feign 调桥接服务，拿原始 feign.Response，
     * 在异步线程中逐块读 InputStream 转发到 SseEmitter。
     */
    @Override
    public SseEmitter stream(String memoryId, String message) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("请先登录"));
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(1800000L);
        String jwtToken = currentJwtToken();
        String userIdStr = userId.toString();

        emitter.onTimeout(() -> { log.warn("SSE 流超时"); emitter.complete(); });
        emitter.onCompletion(() -> log.info("SSE 流已完成"));
        emitter.onError(error -> { log.error("SSE 流发生错误", error); emitter.complete(); });

        // 异步线程执行 Feign 调用与流式读取，避免阻塞 Servlet 线程
        new Thread(() -> {
            try (Response response = aiBridgeClient.streamChat(message, memoryId, userIdStr, jwtToken)) {
                if (response.body() != null) {
                    try (InputStream is = response.body().asInputStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            emitter.send(SseEmitter.event()
                                    .data(new String(buffer, 0, len), MediaType.TEXT_PLAIN)
                                    .name("message"));
                        }
                    }
                }
                emitter.send(SseEmitter.event().data("[DONE]", MediaType.TEXT_PLAIN).name("message"));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 流式对话转发失败", e);
                emitter.completeWithError(e);
            }
        }, "sse-stream-" + memoryId).start();

        return emitter;
    }

    /**
     * 知识库流式对话：桥接服务 /file/chat 为非流式，这里直接转发其返回结果。
     * 保留 SseEmitter 输出形式以兼容原接口签名。
     */
    @Override
    public SseEmitter fileStream(String sessionId, String message) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("请先登录"));
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(1800000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.error("知识库 SSE 流发生错误", error));
        try {
            R<Map<String, Object>> r = aiBridgeClient.knowledgeChat(message, sessionId, userId.toString());
            String answer = "";
            if (r.success() && r.getData() != null) {
                Object ans = r.getData().get("answer");
                answer = ans == null ? "" : ans.toString();
            }
            emitter.send(SseEmitter.event()
                    .data(answer, MediaType.TEXT_PLAIN)
                    .name("message"));
            emitter.send(SseEmitter.event()
                    .data("[DONE]", MediaType.TEXT_PLAIN)
                    .name("message"));
            emitter.complete();
        } catch (IOException e) {
            log.error("发送知识库 SSE 消息失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 测试流式接口：与正式流式对话一致，均委托桥接服务。
     */
    @Override
    public SseEmitter test(String sessionId, String message) {
        return stream(sessionId, message);
    }

    /**
     * 从当前 Servlet 请求中提取 Authorization 头（网关透传的 JWT token），
     * 用于转发给桥接服务做鉴权。
     */
    private String currentJwtToken() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader(HttpHeaders.AUTHORIZATION);
        } catch (Exception e) {
            log.warn("提取 Authorization 头失败", e);
            return null;
        }
    }
}
