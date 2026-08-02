package com.mkz.common.utils;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消息消费幂等工具
 * 基于 Redis SET NX EX 实现消费去重，防止消息重复消费。
 * 消费者处理消息前调用 checkAndMark，已消费过则跳过。
 *
 * 实现说明：mkz-common 未引入 spring-data-redis，故复用已有的 RedissonClient；
 * RBucket.trySet(value, ttl, unit) 等价于 Redis 的 SET key value NX EX。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RedissonClient.class)
public class MessageIdempotentUtil {

    private final RedissonClient redissonClient;

    private static final String KEY_PREFIX = "mq:consumed:";
    private static final long DEFAULT_TTL_HOURS = 24;

    /**
     * 校验并标记消息已消费
     *
     * @param businessId 业务唯一标识（幂等键）
     * @return true 表示首次消费，可继续处理；false 表示已消费过，应跳过
     */
    public boolean checkAndMark(String businessId) {
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + businessId);
        // trySet：仅当 key 不存在时设置成功，等价于 SET NX EX
        return bucket.trySet("1", DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 移除消费幂等标记（业务处理失败时回滚标记，使消息重投后可再次消费）
     *
     * @param businessId 业务唯一标识（幂等键）
     */
    public void remove(String businessId) {
        if (businessId == null) {
            return;
        }
        redissonClient.getBucket(KEY_PREFIX + businessId).delete();
    }
}
