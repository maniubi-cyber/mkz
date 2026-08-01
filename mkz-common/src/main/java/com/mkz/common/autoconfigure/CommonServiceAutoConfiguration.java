package com.mkz.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.common.autoconfigure.mq.RocketMqHelper;
import com.mkz.common.handler.LocalMessageCompensateJob;
import com.mkz.common.mapper.LocalMessageMapper;
import com.mkz.common.service.LocalMessageService;
import com.mkz.common.service.impl.LocalMessageServiceImpl;
import com.mkz.common.utils.MessageIdempotentUtil;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地消息表与幂等消费基础设施自动装配
 * <p>
 * 业务模块的 @SpringBootApplication 位于 com.mkz.xxx 子包，
 * 默认 ComponentScan 不会扫描到 com.mkz.common 下的 @Service/@Component/@Mapper，
 * 因此通过 spring.factories 自动装配以下核心组件：
 * <ul>
 *   <li>LocalMessageMapper：本地消息表Mapper（@MapperScan 扫描 com.mkz.common.mapper）</li>
 *   <li>LocalMessageService：本地消息表服务（保存+异步发送+补偿）</li>
 *   <li>LocalMessageCompensateJob：XXL-Job 补偿任务</li>
 *   <li>MessageIdempotentUtil：基于 Redisson 的消费幂等工具</li>
 * </ul>
 * <p>
 * 仅在启用 RocketMQ（rocketmq.name-server 已配置且 RocketMQTemplate 在类路径）时生效，
 * 不影响未迁移到 RocketMQ 的模块（如点赞、短信等仍走 RabbitMQ）。
 */
@Configuration
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@MapperScan("com.mkz.common.mapper")
public class CommonServiceAutoConfiguration {

    /**
     * 消费幂等工具（基于 Redisson SET NX EX）
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageIdempotentUtil messageIdempotentUtil(RedissonClient redissonClient) {
        return new MessageIdempotentUtil(redissonClient);
    }

    /**
     * 本地消息表服务
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalMessageService localMessageService(LocalMessageMapper localMessageMapper,
                                                   RocketMqHelper rocketMqHelper,
                                                   ObjectMapper objectMapper) {
        return new LocalMessageServiceImpl(localMessageMapper, rocketMqHelper, objectMapper);
    }

    /**
     * 本地消息表补偿任务（XXL-Job）
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalMessageCompensateJob localMessageCompensateJob(LocalMessageService localMessageService) {
        return new LocalMessageCompensateJob(localMessageService);
    }
}
