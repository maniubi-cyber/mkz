package com.mkz.promotion.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.mq.AbstractIdempotentListener;
import com.mkz.common.utils.MessageIdempotentUtil;
import com.mkz.promotion.domain.dto.UserCouponDTO;
import com.mkz.promotion.service.IUserCouponService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 优惠券领取消息消费者
 * <p>
 * 消费 mkz-promotion 内部 RocketMQ 消息（领取/兑换），异步落库用户券。
 * 采用本地消息表 + 幂等消费基类保障最终一致性，businessId（消息keys）作为消费幂等键。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.Topic.PROMOTION_TOPIC,
        consumerGroup = MqConstants.ConsumerGroup.USER_COUPON_SAVE,
        messageModel = MessageModel.CLUSTERING
)
public class PromotionCouponHandler extends AbstractIdempotentListener<UserCouponDTO> implements RocketMQListener<MessageExt> {

    private final IUserCouponService userCouponService;
    private final ObjectMapper objectMapper;

    public PromotionCouponHandler(MessageIdempotentUtil idempotentUtil,
                                  IUserCouponService userCouponService,
                                  ObjectMapper objectMapper) {
        super(idempotentUtil);
        this.userCouponService = userCouponService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String businessId = message.getKeys();
        UserCouponDTO dto;
        try {
            dto = objectMapper.readValue(message.getBody(), UserCouponDTO.class);
        } catch (IOException e) {
            log.error("解析优惠券领取消息失败，keys={}", businessId, e);
            return;
        }
        // businessId 作为消费幂等键，consume 内部先 checkAndMark 再调 doConsume
        consume(businessId, dto);
    }

    @Override
    protected void doConsume(UserCouponDTO msg) {
        log.info("收到了领券消息！:{}", msg);
        userCouponService.checkAndCreateUserCouponNew(msg);
    }
}
