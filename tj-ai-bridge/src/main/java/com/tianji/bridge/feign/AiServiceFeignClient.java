package com.mkz.bridge.feign;

import com.mkz.bridge.config.FeignMultipartConfig;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * OpenFeign 声明式调用 Python AI 服务（FastAPI + LangChain + Chroma）。
 *
 * <p>Python 服务不在 Nacos 注册中心，通过 url 直连指定地址（${ai.service.url}）。
 * 所有方法与 Python 路由一一对应，参数名保持一致。</p>
 *
 * <p>SSE 流式接口返回 {@link feign.Response}，由 Controller 层手动消费 InputStream 透传流，
 * 因为 OpenFeign 的 Decoder 不原生支持 SSE 事件流的声明式解码。</p>
 */
@FeignClient(name = "ai-python-service", url = "${ai.service.url}", configuration = FeignMultipartConfig.class)
public interface AiServiceFeignClient {

    /* ==================== 对话接口 ==================== */

    /** 非流式对话：GET /chat/simple */
    @GetMapping("/chat/simple")
    Map<String, Object> chat(@RequestParam("message") String message,
                             @RequestParam("sessionId") String sessionId,
                             @RequestParam("userId") String userId,
                             @RequestHeader("Authorization") String authorization);

    /**
     * SSE 流式对话：GET /chat/
     * 返回原始 feign.Response，由 Controller 手动读取 InputStream 透传 SSE 事件流。
     */
    @GetMapping(value = "/chat/", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Response streamChat(@RequestParam("message") String message,
                        @RequestParam("sessionId") String sessionId,
                        @RequestParam("userId") String userId,
                        @RequestHeader("Authorization") String authorization);

    /** 清空会话记忆：DELETE /chat/{sessionId} */
    @DeleteMapping("/chat/{sessionId}")
    Map<String, Object> clearMemory(@PathVariable("sessionId") String sessionId,
                                    @RequestParam("userId") String userId);

    /* ==================== 知识库接口 ==================== */

    /** 上传 Markdown 文档：POST /file/upload（multipart/form-data） */
    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Object> uploadMarkdown(@RequestPart("file") MultipartFile file,
                                       @RequestParam("userId") String userId,
                                       @RequestParam("docId") String docId);

    /** 知识库对话：GET /file/chat */
    @GetMapping("/file/chat")
    Map<String, Object> knowledgeChat(@RequestParam("question") String question,
                                      @RequestParam("sessionId") String sessionId,
                                      @RequestParam("userId") String userId);

    /** 删除知识库文档：DELETE /file/{docId} */
    @DeleteMapping("/file/{docId}")
    Map<String, Object> deleteDoc(@PathVariable("docId") String docId,
                                  @RequestParam("userId") String userId);

    /* ==================== 审批接口 ==================== */

    /** 待审批列表：GET /approval/pending */
    @GetMapping("/approval/pending")
    List<Map<String, Object>> pendingApprovals(@RequestParam("userId") String userId);

    /** 审批详情：GET /approval/{id} */
    @GetMapping("/approval/{id}")
    Map<String, Object> approvalDetail(@PathVariable("id") String id);

    /** 审批通过：PUT /approval/{id}/approve */
    @PutMapping("/approval/{id}/approve")
    Map<String, Object> approve(@PathVariable("id") String id);

    /** 审批拒绝：PUT /approval/{id}/reject */
    @PutMapping("/approval/{id}/reject")
    Map<String, Object> reject(@PathVariable("id") String id);
}
