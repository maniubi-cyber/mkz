package com.example.rag.service;

import com.example.rag.dto.response.ChatConversationResponse;
import com.example.rag.dto.response.ChatMessageResponse;
import com.example.rag.dto.response.PageResponse;

/**
 * 聊天会话服务 —— 会话 / 消息的持久化与 RAG 问答转发。
 *
 * <p>前端问答经 Java 转发 Python AI 服务（携带内部签名头），
 * 用户提问与 AI 回答均落库，可跨会话历史查看与续聊。</p>
 *
 * @author knowledge-rag-team
 */
public interface ChatService {

    /**
     * 创建会话
     *
     * @param kbId  关联知识库 ID
     * @param title 会话标题（可为空，首条提问后自动生成）
     */
    ChatConversationResponse createConversation(Long kbId, String title);

    /**
     * 分页查询当前用户的会话列表（按更新时间倒序）
     */
    PageResponse<ChatConversationResponse> listConversations(int page, int size);

    /**
     * 删除会话（软删除，仅本人）
     */
    void deleteConversation(Long conversationId);

    /**
     * 分页查询会话消息（按创建时间正序）
     */
    PageResponse<ChatMessageResponse> listMessages(Long conversationId, int page, int size);

    /**
     * 发送消息：落库用户提问 → 转发 Python RAG 问答 → 落库 AI 回答
     *
     * <p>首条提问时自动用问题前缀生成会话标题。</p>
     *
     * @param conversationId 会话 ID
     * @param question       用户问题
     * @return AI 回答消息
     */
    ChatMessageResponse sendMessage(Long conversationId, String question);
}
