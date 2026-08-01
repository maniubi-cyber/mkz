package com.mkz.live.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.api.dto.live.LiveStartMsgDTO;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.mq.AbstractIdempotentListener;
import com.mkz.common.utils.MessageIdempotentUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 直播开始消息监听器（直播服务自身订阅）
 * <p>
 * 消费直播服务发布的 live.start 消息，作为开播后的异步处理入口。
 * 开播提醒站内信由消息中心 {@code LiveStartNoticeListener}（独立消费组）写入，
 * 本监听器当前仅记录日志，后续可扩展开播统计等内部处理。
 * 采用 {@link AbstractIdempotentListener} 幂等消费，businessId（消息keys）作为消费幂等键。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.Topic.LIVE_TOPIC,
        consumerGroup = MqConstants.ConsumerGroup.LIVE_GROUP,
        selectorExpression = MqConstants.Tag.LIVE_START_TAG,
        messageModel = MessageModel.CLUSTERING
)
public class LiveRoomMqListener extends AbstractIdempotentListener<LiveStartMsgDTO>
        implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;

    public LiveRoomMqListener(MessageIdempotentUtil idempotentUtil, ObjectMapper objectMapper) {
        super(idempotentUtil);
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String businessId = message.getKeys();
        LiveStartMsgDTO dto;
        try {
            dto = objectMapper.readValue(message.getBody(), LiveStartMsgDTO.class);
        } catch (IOException e) {
            log.error("解析直播开始消息失败，keys={}", businessId, e);
            return;
        }
        if (dto == null || dto.getLiveId() == null) {
            log.error("直播开始消息体非法，keys={}", businessId);
            return;
        }
        // keys 为空时用 liveId 兜底，保证幂等键稳定
        if (businessId == null) {
            businessId = "live-start-" + dto.getLiveId();
        }
        consume(businessId, dto);
    }

    @Override
    protected void doConsume(LiveStartMsgDTO dto) {
        log.info("直播开始：直播间={}，标题={}，需通知报名用户数={}",
                dto.getLiveId(), dto.getTitle(),
                dto.getUserIds() == null ? 0 : dto.getUserIds().size());
        // TODO 后续对接消息中心，向 dto.getUserIds() 推送开播提醒
    }
}
