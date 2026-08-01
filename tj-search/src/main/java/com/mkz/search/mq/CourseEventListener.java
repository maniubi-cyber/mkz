package com.mkz.search.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.mq.AbstractIdempotentListener;
import com.mkz.common.utils.MessageIdempotentUtil;
import com.mkz.search.service.ICourseService;
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

import static com.mkz.common.constants.MqConstants.Exchange.COURSE_EXCHANGE;
import static com.mkz.common.constants.MqConstants.Key.COURSE_EXPIRE_KEY;

/**
 * 课程事件监听器
 * <p>
 * 课程上架/下架：通过 RocketMQ 异步解耦（生产者 tj-course），消费时同步搜索索引。
 * 采用本地消息表 + 幂等消费基类保障最终一致性，businessId（消息keys）作为消费幂等键。
 * <p>
 * 课程过期：仍走 RabbitMQ（非本次迁移场景，保持兼容）。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.Topic.COURSE_TOPIC,
        consumerGroup = MqConstants.ConsumerGroup.COURSE_SEARCH,
        messageModel = MessageModel.CLUSTERING
)
public class CourseEventListener extends AbstractIdempotentListener<Long> implements RocketMQListener<MessageExt> {

    private final ICourseService courseService;
    private final ObjectMapper objectMapper;

    /**
     * 暂存当前消息的 tag（up/down），供 doConsume 区分上下架操作
     */
    private final ThreadLocal<String> actionHolder = new ThreadLocal<>();

    public CourseEventListener(MessageIdempotentUtil idempotentUtil,
                               ICourseService courseService,
                               ObjectMapper objectMapper) {
        super(idempotentUtil);
        this.courseService = courseService;
        this.objectMapper = objectMapper;
    }

    /**
     * RocketMQ 消费入口：解析 tag/keys/body，委托父类做幂等校验后处理
     */
    @Override
    public void onMessage(MessageExt message) {
        String tags = message.getTags();
        String businessId = message.getKeys();
        Long courseId;
        try {
            courseId = objectMapper.readValue(message.getBody(), Long.class);
        } catch (IOException e) {
            log.error("解析课程消息失败，tags={}, keys={}", tags, businessId, e);
            return;
        }
        try {
            actionHolder.set(tags);
            // businessId 作为消费幂等键，consume 内部先 checkAndMark 再调 doConsume
            consume(businessId, courseId);
        } finally {
            actionHolder.remove();
        }
    }

    /**
     * 实际业务处理：根据 tag 区分上架/下架，同步搜索索引
     */
    @Override
    protected void doConsume(Long courseId) {
        String action = actionHolder.get();
        log.debug("处理课程{}消息，courseId={}", action, courseId);
        if ("up".equals(action)) {
            courseService.handleCourseUp(courseId);
        } else if ("down".equals(action)) {
            courseService.handleCourseDelete(courseId);
        } else {
            log.warn("未知的课程操作tag: {}", action);
        }
    }

    /**
     * 课程过期：仍走 RabbitMQ（非迁移场景，保持兼容）
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "search.course.expire.queue", durable = "true"),
            exchange = @Exchange(name = COURSE_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = COURSE_EXPIRE_KEY
    ))
    public void listenCourseExpire(Long courseId) {
        log.debug("监听到课程过期");
        courseService.handleCourseDelete(courseId);
    }
}
