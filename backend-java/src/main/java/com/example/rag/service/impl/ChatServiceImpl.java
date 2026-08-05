package com.example.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.client.AiServiceClient;
import com.example.rag.common.BusinessException;
import com.example.rag.common.SecurityUtils;
import com.example.rag.dto.response.ChatConversationResponse;
import com.example.rag.dto.response.ChatMessageResponse;
import com.example.rag.dto.response.PageResponse;
import com.example.rag.entity.Conversation;
import com.example.rag.entity.Message;
import com.example.rag.mapper.ConversationMapper;
import com.example.rag.mapper.MessageMapper;
import com.example.rag.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话服务实现
 *
 * <p>链路：会话/消息落库（MySQL）→ Java 转发 Python /ai/chat（内部签名鉴权）
 * → AI 回答 + 引用来源落库。历史消息取最近 {@link #HISTORY_LIMIT} 条
 * 作为上下文随请求转发。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 转发 Python 时携带的历史消息条数上限 */
    private static final int HISTORY_LIMIT = 10;

    /** 首条提问自动生成标题的字数上限 */
    private static final int TITLE_MAX_LEN = 30;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AiServiceClient aiServiceClient;

    @Override
    public ChatConversationResponse createConversation(Long kbId, String title) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        Conversation conversation = new Conversation();
        conversation.setUserId(currentUserId);
        conversation.setKbId(kbId);
        conversation.setTitle(StringUtils.hasText(title) ? title : "新会话");
        conversation.setIsDeleted(0);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.insert(conversation);

        log.info("会话创建成功: conversationId={}, userId={}, kbId={}",
                conversation.getId(), currentUserId, kbId);
        return ChatConversationResponse.from(conversation);
    }

    @Override
    public PageResponse<ChatConversationResponse> listConversations(int page, int size) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        Page<Conversation> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, currentUserId)
                .orderByDesc(Conversation::getUpdateTime);

        return PageResponse.from(
                conversationMapper.selectPage(pageObj, wrapper),
                ChatConversationResponse::from);
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        Conversation conversation = getOwnedConversation(conversationId);
        conversationMapper.deleteById(conversation.getId());
        // 连带软删除/物理删除消息（消息表无 @TableLogic，直接物理删除）
        messageMapper.delete(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId));
        log.info("会话已删除: conversationId={}, userId={}", conversationId, conversation.getUserId());
    }

    @Override
    public PageResponse<ChatMessageResponse> listMessages(Long conversationId, int page, int size) {
        getOwnedConversation(conversationId);

        Page<Message> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreateTime);

        return PageResponse.from(
                messageMapper.selectPage(pageObj, wrapper),
                ChatMessageResponse::from);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long conversationId, String question) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Conversation conversation = getOwnedConversation(conversationId);

        // 1. 落库用户提问
        Message userMessage = new Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(question);
        userMessage.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMessage);

        // 2. 首条提问自动生成会话标题（取问题前 TITLE_MAX_LEN 字）
        boolean isFirstQuestion = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)) == 1;
        if (isFirstQuestion && !StringUtils.hasText(conversation.getTitle())) {
            String title = question.length() > TITLE_MAX_LEN
                    ? question.substring(0, TITLE_MAX_LEN) : question;
            conversation.setTitle(title);
            conversation.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }

        // 3. 转发 Python RAG 问答（内部签名头由 RestTemplate 拦截器自动附加）
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kb_id", conversation.getKbId());
        request.put("question", question);
        request.put("conversation_id", conversationId);
        request.put("history", buildHistoryPayload(conversationId));
        request.put("user_id", currentUserId);
        request.put("role", SecurityUtils.isAdmin() ? "ADMIN" : "USER");
        Long orgId = SecurityUtils.getCurrentUserOrgId();
        if (orgId != null) {
            request.put("org_id", orgId);
        }

        Map<String, Object> response = aiServiceClient.chat(request);

        // 4. 落库 AI 回答
        String answer = response != null && response.get("answer") != null
                ? String.valueOf(response.get("answer")) : "";
        Message assistantMessage = new Message();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        if (response != null && response.get("sources") != null) {
            assistantMessage.setReferencesJson(toJson(response.get("sources")));
        }
        if (response != null && response.get("token_usage") != null) {
            assistantMessage.setTokenUsage(parseTokenUsage(response.get("token_usage")));
        }
        assistantMessage.setCreateTime(LocalDateTime.now());
        messageMapper.insert(assistantMessage);

        // 5. 刷新会话更新时间
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        log.info("问答完成: conversationId={}, userId={}, userMsgId={}, assistantMsgId={}",
                conversationId, currentUserId, userMessage.getId(), assistantMessage.getId());
        return ChatMessageResponse.from(assistantMessage);
    }

    // ==================== 私有方法 ====================

    private Conversation getOwnedConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在");
        }
        // 仅本人可访问自己的会话（admin 除外）
        if (!SecurityUtils.isAdmin()
                && !conversation.getUserId().equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return conversation;
    }

    /** 取会话最近 HISTORY_LIMIT 条消息作为多轮上下文 */
    private List<Map<String, Object>> buildHistoryPayload(Long conversationId) {
        List<Message> recent = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getId)
                        .last("LIMIT " + HISTORY_LIMIT));
        // 消息按创建时间正序返回给 Python
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            Message m = recent.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            history.add(item);
        }
        return history;
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("引用来源序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private Integer parseTokenUsage(Object tokenUsage) {
        try {
            if (tokenUsage instanceof Map<?, ?> map) {
                int prompt = map.containsKey("prompt_tokens")
                        ? ((Number) map.get("prompt_tokens")).intValue() : 0;
                int completion = map.containsKey("completion_tokens")
                        ? ((Number) map.get("completion_tokens")).intValue() : 0;
                return prompt + completion;
            }
        } catch (Exception e) {
            log.warn("token 统计解析失败: {}", e.getMessage());
        }
        return null;
    }
}
