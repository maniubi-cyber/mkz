package com.mkz.message.service;

import com.mkz.api.dto.sms.SmsInfoDTO;
import com.mkz.api.dto.user.UserDTO;
import com.mkz.message.domain.po.NoticeTemplate;

import java.util.List;

public interface ISmsService {
    void sendMessageByTemplate(NoticeTemplate noticeTemplate, List<UserDTO> users);

    void sendMessage(SmsInfoDTO smsInfoDTO);

    void sendMessageAsync(SmsInfoDTO smsInfoDTO);
}
