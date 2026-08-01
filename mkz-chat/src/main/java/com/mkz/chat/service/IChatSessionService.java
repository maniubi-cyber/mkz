package com.mkz.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.chat.domain.po.ChatSession;
import com.mkz.chat.domain.query.RecordQuery;
import com.mkz.common.domain.dto.PageDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * <p>
 * 聊天对话的每个片段记录（分片存储） 服务类
 * </p>
 *
 * @author lusy
 * @since 2025-05-06
 */
public interface IChatSessionService extends IService<ChatSession> {

    String chat(String sessionId, String message);

    SseEmitter stream(String sessionId, String message);

    PageDTO<ChatSession> getRecord(RecordQuery query);

    SseEmitter fileStream(String sessionId, String message);

    SseEmitter test(String sessionId, String message);
}
