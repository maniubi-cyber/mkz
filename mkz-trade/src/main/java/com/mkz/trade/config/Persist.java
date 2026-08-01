package com.mkz.trade.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.statemachine.persist.RepositoryStateMachinePersist;
import org.springframework.statemachine.redis.RedisStateMachineContextRepository;
import org.springframework.statemachine.redis.RedisStateMachinePersister;

import javax.annotation.Resource;

/**
 * 订单状态机持久化配置
 * <p>
 * 使用 Redis 持久化，保证状态机状态在分布式/多实例部署下可跨实例、跨重启恢复，
 * 避免内存 HashMap 持久化带来的非线程安全、状态丢失、已支付订单被重复 PAYED 等问题。
 */
@Configuration
@Slf4j
public class Persist<E, S> {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 持久化到 redis 中，在分布式系统中使用
     *
     * @return Redis 状态机持久化器
     */
    @Bean(name = "stateMachineRedisPersister")
    public RedisStateMachinePersister<E, S> getRedisPersister() {
        RedisStateMachineContextRepository<E, S> repository = new RedisStateMachineContextRepository<>(redisConnectionFactory);
        RepositoryStateMachinePersist<E, S> p = new RepositoryStateMachinePersist<>(repository);
        return new RedisStateMachinePersister<>(p);
    }
}
