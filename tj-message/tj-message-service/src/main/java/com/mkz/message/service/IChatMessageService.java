package com.mkz.message.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.message.domain.dto.ChatMessageDTO;
import com.mkz.message.domain.po.ChatMessage;
import com.mkz.message.domain.query.ChatHistoryQuery;
import com.mkz.message.domain.vo.ChatMessageVO;

import java.util.List;

public interface IChatMessageService {
    Long sendMessage(ChatMessageDTO messageDTO);
    PageDTO<ChatMessageVO> getHistoryMessages(ChatHistoryQuery query);
    void markMessagesAsRead(List<Long> messageIds);
}