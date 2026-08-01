package com.mkz.bridge.controller;

import com.mkz.bridge.feign.AiServiceFeignClient;
import com.mkz.common.domain.R;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * AI 桥接 Controller。
 * 对外暴露 REST 接口，内部通过 OpenFeign 调用 Python AI 服务，
 * 并将 Python 返回结果封装为项目统一响应体 {@link R}。
 *
 * <p>SSE 流式接口不封装 R，直接透传事件流（SSE 是多事件流，不适用单次响应封装）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiBridgeController {

    private final AiServiceFeignClient feignClient;

    /* ==================== 对话接口 ==================== */

    /** 非流式对话：封装 Python 返回为 R */
    @GetMapping("/chat/simple")
    public R<Map<String, Object>> chat(@RequestParam String message,
                                       @RequestParam String sessionId,
                                       @RequestParam String userId,
                                       @RequestHeader("Authorization") String auth) {
        try {
            Map<String, Object> data = feignClient.chat(message, sessionId, userId, auth);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 非流式对话失败: sessionId={}", sessionId, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /**
     * SSE 流式对话：透传 Python 的 SSE 事件流（不封装 R）。
     * OpenFeign 返回原始 feign.Response，Controller 逐块读取 InputStream 转发给前端。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamChat(@RequestParam String message,
                                            @RequestParam String sessionId,
                                            @RequestParam String userId,
                                            @RequestHeader("Authorization") String auth) {
        return output -> {
            try (Response response = feignClient.streamChat(message, sessionId, userId, auth)) {
                if (response.body() == null) {
                    return;
                }
                try (InputStream is = response.body().asInputStream()) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                        output.flush();
                    }
                }
            } catch (Exception e) {
                log.error("SSE 流式对话转发失败: sessionId={}", sessionId, e);
            }
        };
    }

    /** 清空会话记忆：封装 Python 返回为 R */
    @DeleteMapping("/chat/{sessionId}")
    public R<Map<String, Object>> clearMemory(@PathVariable String sessionId,
                                              @RequestParam String userId) {
        try {
            Map<String, Object> data = feignClient.clearMemory(sessionId, userId);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 清空记忆失败: sessionId={}", sessionId, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /* ==================== 知识库接口 ==================== */

    /** 上传 Markdown 文档：封装 Python 返回为 R */
    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> uploadMarkdown(@RequestPart("file") MultipartFile file,
                                                 @RequestParam String userId,
                                                 @RequestParam String docId) {
        try {
            Map<String, Object> data = feignClient.uploadMarkdown(file, userId, docId);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 上传文档失败: docId={}", docId, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /** 知识库对话：封装 Python 返回为 R */
    @GetMapping("/file/chat")
    public R<Map<String, Object>> knowledgeChat(@RequestParam String question,
                                                @RequestParam String sessionId,
                                                @RequestParam String userId) {
        try {
            Map<String, Object> data = feignClient.knowledgeChat(question, sessionId, userId);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 知识库对话失败: sessionId={}", sessionId, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /** 删除知识库文档：封装 Python 返回为 R */
    @DeleteMapping("/file/{docId}")
    public R<Map<String, Object>> deleteDoc(@PathVariable String docId,
                                            @RequestParam String userId) {
        try {
            Map<String, Object> data = feignClient.deleteDoc(docId, userId);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 删除文档失败: docId={}", docId, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /* ==================== 审批接口 ==================== */

    /** 待审批列表：封装 Python 返回为 R */
    @GetMapping("/approval/pending")
    public R<List<Map<String, Object>>> pendingApprovals(@RequestParam(required = false) String userId) {
        try {
            List<Map<String, Object>> data = feignClient.pendingApprovals(userId);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 待审批列表失败: userId={}", userId, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /** 审批详情：封装 Python 返回为 R */
    @GetMapping("/approval/{id}")
    public R<Map<String, Object>> approvalDetail(@PathVariable String id) {
        try {
            Map<String, Object> data = feignClient.approvalDetail(id);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 审批详情失败: id={}", id, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /** 审批通过：封装 Python 返回为 R */
    @PutMapping("/approval/{id}/approve")
    public R<Map<String, Object>> approve(@PathVariable String id) {
        try {
            Map<String, Object> data = feignClient.approve(id);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 审批通过失败: id={}", id, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }

    /** 审批拒绝：封装 Python 返回为 R */
    @PutMapping("/approval/{id}/reject")
    public R<Map<String, Object>> reject(@PathVariable String id) {
        try {
            Map<String, Object> data = feignClient.reject(id);
            return R.ok(data);
        } catch (Exception e) {
            log.error("调用 Python 审批拒绝失败: id={}", id, e);
            return R.error("AI 服务调用失败: " + e.getMessage());
        }
    }
}
