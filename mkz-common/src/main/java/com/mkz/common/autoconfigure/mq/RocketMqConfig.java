package com.mkz.common.autoconfigure.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类
 * 
 * 启用RocketMQ作为消息中间件：
 * - 课程数据变更、优惠券发放等操作通过RocketMQ异步解耦
 * - 建立本地消息表 + XXL-Job定时扫描补偿机制
 * - 确保消息在异常情况下的最终一致性
 */
@Configuration
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMqConfig {

    /**
     * 配置RocketMqHelper Bean
     *
     * @param rocketMQTemplate RocketMQ模板
     * @return RocketMqHelper实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RocketMqHelper rocketMqHelper(RocketMQTemplate rocketMQTemplate) {
        return new RocketMqHelper(rocketMQTemplate);
    }
}
