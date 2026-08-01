package com.mkz.message.thirdparty;

import com.mkz.api.dto.sms.SmsInfoDTO;
import com.mkz.message.domain.po.MessageTemplate;

/**
 * 第三方接口对接平台
 */
public interface ISmsHandler {

    /**
     * 发送短信
     */
    void send(SmsInfoDTO platformSmsInfoDTO, MessageTemplate template);


}
