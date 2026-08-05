package com.example.rag.dto.response;

import com.example.rag.entity.Conversation;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话响应 DTO
 *
 * @author knowledge-rag-team
 */
@Data
@Builder
public class ChatConversationResponse {

    private Long id;

    private Long kbId;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static ChatConversationResponse from(Conversation c) {
        return ChatConversationResponse.builder()
                .id(c.getId())
                .kbId(c.getKbId())
                .title(c.getTitle())
                .createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime())
                .build();
    }
}
