package com.mkz.chat.controller;

import com.mkz.chat.domain.po.ChatSession;
import com.mkz.chat.domain.query.RecordQuery;
import com.mkz.chat.feign.AiBridgeClient;
import com.mkz.common.annotations.NoWrapper;
import com.mkz.common.domain.R;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.utils.UserContext;
import feign.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 聊天接口（薄网关层）。
 * <p>
 * 鉴权与会话管理在 Java 侧完成，AI 能力通过 Feign 调用 mkz-ai-bridge 桥接服务，
 * 桥接服务再用 OpenFeign 调 Python AI 服务并封装统一响应体。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
@Slf4j
@Api(tags = "聊天接口")
public class ChatController {

    private final com.mkz.chat.service.IChatSessionService chatSessionService;
    private final AiBridgeClient aiBridgeClient;

    @NoWrapper
    @ApiOperation("普通聊天，非流式")
    @GetMapping("/simple")
    public R<Map<String, Object>> memoryChatRedis(@RequestParam(defaultValue = "我叫finch，你叫什么名字？") String message,
                                                  @RequestParam(defaultValue = "1") String sessionId,
                                                  @RequestHeader(value = "Authorization", required = false) String auth) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        return aiBridgeClient.chat(message, sessionId, userId.toString(), auth);
    }

    @NoWrapper
    @ApiOperation("流式聊天")
    @GetMapping(value = "/", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter memoryChatRedisStream(@RequestParam(defaultValue = "我叫finch，你叫什么名字？") String message,
                                            @RequestParam(defaultValue = "1") String sessionId,
                                            @RequestHeader(value = "Authorization", required = false) String auth) {
        SseEmitter emitter = new SseEmitter(0L);
        Long userId = UserContext.getUser();
        if (userId == null) {
            emitter.completeWithError(new RuntimeException("请先登录"));
            return emitter;
        }
        // 通过 Feign 调桥接服务，拿原始 Response 逐块读 InputStream 转发到 SseEmitter
        try (Response response = aiBridgeClient.streamChat(message, sessionId, userId.toString(), auth)) {
            if (response.body() != null) {
                try (InputStream is = response.body().asInputStream()) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        emitter.send(SseEmitter.event().data(new String(buffer, 0, len)));
                    }
                }
            }
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            log.error("SSE 流式对话转发失败: sessionId={}", sessionId, e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @ApiOperation("根据知识库内容流式聊天")
    @GetMapping(value = "/file", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter memoryChatFileStream(@RequestParam(defaultValue = "我叫finch，你叫什么名字？") String message,
                                           @RequestParam(defaultValue = "1") String sessionId) {
        return chatSessionService.fileStream(sessionId, message);
    }

    @ApiOperation("测试流式聊天接口")
    @GetMapping(value = "/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter test(@RequestParam(defaultValue = "我叫finch，你叫什么名字？") String message,
                           @RequestParam(defaultValue = "1") String sessionId) {
        return chatSessionService.test(sessionId, message);
    }

    @ApiOperation("获取聊天记录")
    @GetMapping("/records")
    public PageDTO<ChatSession> getRecord(RecordQuery query) {
        return chatSessionService.getRecord(query);
    }

    @NoWrapper
    @ApiOperation("清空会话记忆（委托桥接服务）")
    @DeleteMapping("/{sessionId}")
    public R<Map<String, Object>> clearMemory(@PathVariable("sessionId") String sessionId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        return aiBridgeClient.clearMemory(sessionId, userId.toString());
    }
}
