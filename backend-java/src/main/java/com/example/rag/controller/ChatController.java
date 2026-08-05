package com.example.rag.controller;

import com.example.rag.common.Result;
import com.example.rag.dto.request.ChatConversationRequest;
import com.example.rag.dto.request.ChatMessageRequest;
import com.example.rag.dto.response.ChatConversationResponse;
import com.example.rag.dto.response.ChatMessageResponse;
import com.example.rag.dto.response.PageResponse;
import com.example.rag.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天会话接口
 *
 * <p>会话与消息持久化在 MySQL，RAG 问答经 Java 转发 Python AI 服务。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Tag(name = "聊天会话", description = "会话管理 / 消息记录 / RAG 问答")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "创建会话", description = "关联知识库，后续问答消息均记录在此会话下")
    @PostMapping("/conversations")
    public Result<ChatConversationResponse> createConversation(
            @Valid @RequestBody ChatConversationRequest request) {
        return Result.success(chatService.createConversation(request.getKbId(), request.getTitle()));
    }

    @Operation(summary = "会话列表", description = "当前用户的会话（按更新时间倒序）")
    @GetMapping("/conversations")
    public Result<PageResponse<ChatConversationResponse>> listConversations(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,

            @Parameter(description = "每页条数", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(chatService.listConversations(page, size));
    }

    @Operation(summary = "删除会话", description = "软删除会话并清理其消息记录（仅本人）")
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话 ID", required = true, example = "1")
            @PathVariable Long id) {
        chatService.deleteConversation(id);
        return Result.success(null);
    }

    @Operation(summary = "消息列表", description = "会话下的消息记录（按时间正序）")
    @GetMapping("/conversations/{id}/messages")
    public Result<PageResponse<ChatMessageResponse>> listMessages(
            @Parameter(description = "会话 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,

            @Parameter(description = "每页条数", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(chatService.listMessages(id, page, size));
    }

    @Operation(summary = "发送消息（RAG 问答）", description = """
            用户提问落库 → Java 转发 Python RAG 问答（检索 + LLM 生成）
            → AI 回答与引用来源落库。首条提问自动生成会话标题。
            """)
    @PostMapping("/conversations/{id}/messages")
    public Result<ChatMessageResponse> sendMessage(
            @Parameter(description = "会话 ID", required = true, example = "1")
            @PathVariable Long id,

            @Valid @RequestBody ChatMessageRequest request) {
        return Result.success(chatService.sendMessage(id, request.getQuestion()));
    }
}
