package com.mkz.learning.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.api.dto.trade.OrderBasicDTO;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.mq.AbstractIdempotentListener;
import com.mkz.common.utils.CollUtils;
import com.mkz.common.utils.MessageIdempotentUtil;
import com.mkz.learning.service.ILearningLessonService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 课表变更监听器
 * <p>
 * 订单支付成功：通过 RocketMQ 异步解耦（生产者 mkz-trade），把课程加入用户课表。
 * 采用本地消息表 + 幂等消费基类保障最终一致性，businessId（消息keys）作为消费幂等键。
 * <p>
 * 订单退款：仍走 RabbitMQ（非本次迁移场景，保持兼容）。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.Topic.ORDER_TOPIC,
        consumerGroup = MqConstants.ConsumerGroup.USER_COURSE_SAVE,
        messageModel = MessageModel.CLUSTERING
)
public class LessonChangeListener extends AbstractIdempotentListener<OrderBasicDTO> implements RocketMQListener<MessageExt> {

    private final ILearningLessonService lessonService;
    private final ObjectMapper objectMapper;

    public LessonChangeListener(MessageIdempotentUtil idempotentUtil,
                                ILearningLessonService lessonService,
                                ObjectMapper objectMapper) {
        super(idempotentUtil);
        this.lessonService = lessonService;
        this.objectMapper = objectMapper;
    }

    /**
     * RocketMQ 消费入口：解析 keys/body，委托父类做幂等校验后处理
     */
    @Override
    public void onMessage(MessageExt message) {
        String businessId = message.getKeys();
        OrderBasicDTO dto;
        try {
            dto = objectMapper.readValue(message.getBody(), OrderBasicDTO.class);
        } catch (IOException e) {
            log.error("解析订单支付消息失败，keys={}", businessId, e);
            return;
        }
        // businessId 作为消费幂等键，consume 内部先 checkAndMark 再调 doConsume
        consume(businessId, dto);
    }

    /**
     * 实际业务处理：把课程加入用户课表（报名成功）
     */
    @Override
    protected void doConsume(OrderBasicDTO dto) {
        log.info("LessonChangeListener接收到了消息,用户：{}，课程：{}", dto.getUserId(), dto.getCourseIds());
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            log.error("接收到MQ消息有误，订单数据为空");
            // 此处不能抛异常，否则MQ会不停的重试。
            return;
        }
        // 调用service，保存课程到课表 报名成功
        lessonService.addUserLesson(dto.getUserId(), dto.getCourseIds());
    }

    /**
     * 订单退款：仍走 RabbitMQ（非迁移场景，保持兼容）
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY))
    public void handleRefundMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener接收到了消息,用户：{}，课程：{}", dto.getUserId(), dto.getCourseIds());
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            log.error("接收到MQ消息有误，订单数据为空");
            // 此处不能抛异常，否则MQ会不停的重试。
            return;
        }
        // 调用service，按用户+课程删除课表（退款场景：以消息中的 userId 为准，不依赖线程级 UserContext）
        List<Long> courseIds = dto.getCourseIds();
        for (Long courseId : courseIds) {
            lessonService.deleteUserLesson(dto.getUserId(), courseId);
        }
    }
}
