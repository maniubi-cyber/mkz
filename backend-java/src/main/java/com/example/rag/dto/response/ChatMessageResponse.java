package com.example.rag.dto.response;

import com.example.rag.entity.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息响应 DTO
 *
 * @author knowledge-rag-team
 */
@Data
@Builder
public class ChatMessageResponse {

    private Long id;

    private Long conversationId;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用来源 JSON（assistant 消息） */
    private String referencesJson;

    /** 本次消耗 token 数（assistant 消息） */
    private Integer tokenUsage;

    private LocalDateTime createTime;

    public static ChatMessageResponse from(Message m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .role(m.getRole())
                .content(m.getContent())
                .referencesJson(m.getReferencesJson())
                .tokenUsage(m.getTokenUsage())
                .createTime(m.getCreateTime())
                .build();
    }
}
