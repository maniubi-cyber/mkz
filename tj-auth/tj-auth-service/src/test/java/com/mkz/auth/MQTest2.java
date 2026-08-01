package com.mkz.auth;/**
 * @author fsq
 * @date 2025/6/11 12:38
 */

import com.mkz.api.dto.sms.SmsInfoDTO;
import com.mkz.common.autoconfigure.mq.RocketMqHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @Author: fsq
 * @Date: 2025/6/11 12:38
 * @Version: 1.0
 */
@SpringBootTest
public class MQTest2 {

    @Resource
    private RocketMqHelper rocketMqHelper;

    @Test
    public void test(){
        SmsInfoDTO smsInfoDTO = new SmsInfoDTO();
        smsInfoDTO.setTemplateCode("SMS_251685292");

        rocketMqHelper.sendDelaySync("message", "consumer_group_message", smsInfoDTO, 3);

    }

}
