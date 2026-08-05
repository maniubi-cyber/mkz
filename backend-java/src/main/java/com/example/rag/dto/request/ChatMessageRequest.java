package com.example.rag.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送聊天消息请求
 *
 * @author knowledge-rag-team
 */
@Data
public class ChatMessageRequest {

    /** 用户问题 */
    @NotBlank(message = "问题不能为空")
    private String question;
}
