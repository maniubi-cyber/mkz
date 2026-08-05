package com.example.rag.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求
 *
 * @author knowledge-rag-team
 */
@Data
public class ChatConversationRequest {

    /** 关联知识库 ID */
    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    /** 会话标题（可选，默认取第一条提问前 30 字） */
    private String title;
}
