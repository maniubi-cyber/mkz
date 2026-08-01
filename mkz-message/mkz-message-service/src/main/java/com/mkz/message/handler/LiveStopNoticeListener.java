package com.mkz.message.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.api.dto.live.LiveStopMsgDTO;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.mq.AbstractIdempotentListener;
import com.mkz.common.utils.CollUtils;
import com.mkz.common.utils.MessageIdempotentUtil;
import com.mkz.common.utils.StringUtils;
import com.mkz.message.config.MessageProperties;
import com.mkz.message.domain.po.UserInbox;
import com.mkz.message.enums.NoticeType;
import com.mkz.message.service.IUserInboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 直播结束提醒监听器（消息中心订阅）
 * <p>
 * 消费 mkz-live 发布的 live.stop 消息，向已报名用户批量写入站内信（user_inbox）：
 * "直播结束/回放已生成" = 系统通知（type=0）。独立消费组 {@code LIVE_STOP_NOTICE_GROUP}，
 * 与直播开始消息消费组互不影响。
 * 采用 {@link AbstractIdempotentListener} 幂等消费，businessId（消息keys）作为消费幂等键。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.Topic.LIVE_TOPIC,
        consumerGroup = MqConstants.ConsumerGroup.LIVE_STOP_NOTICE_GROUP,
        selectorExpression = MqConstants.Tag.LIVE_STOP_TAG,
        messageModel = MessageModel.CLUSTERING
)
public class LiveStopNoticeListener extends AbstractIdempotentListener<LiveStopMsgDTO>
        implements RocketMQListener<MessageExt> {

    private final IUserInboxService inboxService;
    private final MessageProperties messageProperties;
    private final ObjectMapper objectMapper;

    public LiveStopNoticeListener(MessageIdempotentUtil idempotentUtil,
                                  IUserInboxService inboxService,
                                  MessageProperties messageProperties,
                                  ObjectMapper objectMapper) {
        super(idempotentUtil);
        this.inboxService = inboxService;
        this.messageProperties = messageProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String businessId = message.getKeys();
        LiveStopMsgDTO dto;
        try {
            dto = objectMapper.readValue(message.getBody(), LiveStopMsgDTO.class);
        } catch (IOException e) {
            log.error("解析直播结束消息失败，keys={}", businessId, e);
            return;
        }
        if (dto == null || dto.getLiveId() == null) {
            log.error("直播结束消息体非法，keys={}", businessId);
            return;
        }
        // keys 为空时用 liveId 兜底，保证幂等键稳定
        if (businessId == null) {
            businessId = "live-stop-" + dto.getLiveId();
        }
        consume(businessId, dto);
    }

    @Override
    protected void doConsume(LiveStopMsgDTO dto) {
        if (CollUtils.isEmpty(dto.getUserIds())) {
            log.info("直播[{}]结束但无报名用户，跳过站内信推送", dto.getLiveId());
            return;
        }
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(messageProperties.getNoticeTtlMonths());
        boolean hasPlayback = StringUtils.isNotBlank(dto.getPlaybackUrl());
        String content = hasPlayback
                ? "您报名的直播《" + dto.getTitle() + "》已结束，回放已生成，可前往直播间观看。"
                : "您报名的直播《" + dto.getTitle() + "》已结束，感谢您的观看。";
        List<UserInbox> boxes = dto.getUserIds().stream().map(userId -> {
            UserInbox box = new UserInbox();
            box.setUserId(userId);
            box.setType(NoticeType.SYSTEM.getValue());
            box.setTitle("直播结束提醒");
            box.setContent(content);
            box.setPublisher(0L); // 0 代表系统
            box.setPushTime(pushTime);
            box.setExpireTime(expireTime);
            return box;
        }).collect(Collectors.toList());
        inboxService.saveBatch(boxes);
        log.info("直播结束提醒已写入站内信，liveId={}，回放={}，推送用户数={}",
                dto.getLiveId(), hasPlayback ? "有" : "无", boxes.size());
    }
}
