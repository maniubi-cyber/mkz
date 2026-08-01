package com.mkz.chat.feign;

import com.mkz.common.domain.R;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 调用 mkz-ai-bridge 桥接服务的 Feign 客户端。
 * 桥接服务内部用 OpenFeign 调 Python AI 服务并封装返回结果为 {@link R}。
 *
 * <p>SSE 流式接口返回原始 {@link feign.Response}，由 Controller 手动消费 InputStream 透传。</p>
 */
@FeignClient(name = "ai-bridge-service")
public interface AiBridgeClient {

    /* ==================== 对话接口 ==================== */

    /** 非流式对话 */
    @GetMapping("/ai/chat/simple")
    R<Map<String, Object>> chat(@RequestParam("message") String message,
                                @RequestParam("sessionId") String sessionId,
                                @RequestParam("userId") String userId,
                                @RequestHeader("Authorization") String authorization);

    /** SSE 流式对话：返回原始 feign.Response 供 Controller 透传 */
    @GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Response streamChat(@RequestParam("message") String message,
                        @RequestParam("sessionId") String sessionId,
                        @RequestParam("userId") String userId,
                        @RequestHeader("Authorization") String authorization);

    /** 清空会话记忆 */
    @DeleteMapping("/ai/chat/{sessionId}")
    R<Map<String, Object>> clearMemory(@PathVariable("sessionId") String sessionId,
                                       @RequestParam("userId") String userId);

    /* ==================== 知识库接口 ==================== */

    /** 上传 Markdown 文档 */
    @PostMapping(value = "/ai/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<Map<String, Object>> uploadMarkdown(@RequestPart("file") MultipartFile file,
                                          @RequestParam("userId") String userId,
                                          @RequestParam("docId") String docId);

    /** 知识库对话 */
    @GetMapping("/ai/file/chat")
    R<Map<String, Object>> knowledgeChat(@RequestParam("question") String question,
                                         @RequestParam("sessionId") String sessionId,
                                         @RequestParam("userId") String userId);

    /** 删除知识库文档 */
    @DeleteMapping("/ai/file/{docId}")
    R<Map<String, Object>> deleteDoc(@PathVariable("docId") String docId,
                                     @RequestParam("userId") String userId);

    /* ==================== 审批接口 ==================== */

    /** 待审批列表 */
    @GetMapping("/ai/approval/pending")
    R<List<Map<String, Object>>> pendingApprovals(@RequestParam("userId") String userId);

    /** 审批详情 */
    @GetMapping("/ai/approval/{id}")
    R<Map<String, Object>> approvalDetail(@PathVariable("id") String id);

    /** 审批通过 */
    @PutMapping("/ai/approval/{id}/approve")
    R<Map<String, Object>> approve(@PathVariable("id") String id);

    /** 审批拒绝 */
    @PutMapping("/ai/approval/{id}/reject")
    R<Map<String, Object>> reject(@PathVariable("id") String id);
}
