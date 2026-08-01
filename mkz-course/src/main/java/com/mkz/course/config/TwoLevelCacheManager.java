package com.mkz.course.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 双层缓存管理器
 *
 * 实现 Caffeine + Redis 双层缓存架构：
 * 1. 读取时先查Caffeine本地缓存（存储的是JSON字符串），命中则反序列化为目标类型后返回
 * 2. Caffeine未命中则查Redis分布式缓存，命中则回写Caffeine并返回
 * 3. Redis未命中则通过Redisson分布式锁回源数据库（双重检查），防止热点Key过期瞬间缓存击穿
 *
 * Caffeine存储Redis对应的JSON字符串，配合CaffeineConfig中的CacheLoader，
 * 在refreshAfterWrite到期时异步从Redis重新加载，既保证类型安全，又避免击穿
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TwoLevelCacheManager {

    /** 抢锁等待时间（秒） */
    private static final long LOCK_WAIT_SECONDS = 2L;
    /** 锁持有时间（秒），保证DB回源后释放 */
    private static final long LOCK_LEASE_SECONDS = 10L;
    /** 未抢到锁时的短暂自旋睡眠（毫秒） */
    private static final long RETRY_SLEEP_MILLIS = 50L;

    private final Cache<String, String> courseLocalCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    /**
     * 获取缓存数据（双层缓存，兼容旧调用方，反序列化为Object）
     */
    public <T> T get(String key, String redisKey, long expireTime, TimeUnit timeUnit, Supplier<T> dbLoader) {
        return get(key, redisKey, expireTime, timeUnit, dbLoader, null);
    }

    /**
     * 获取缓存数据（双层缓存）
     *
     * @param key      本地缓存key
     * @param redisKey Redis key
     * @param expireTime Redis过期时间
     * @param timeUnit   时间单位
     * @param dbLoader   数据库回源函数
     * @param typeRef    目标类型引用，用于从JSON反序列化；为null时按Object.class反序列化（兼容旧调用方）
     * @param <T>        数据类型
     * @return 缓存数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, String redisKey, long expireTime, TimeUnit timeUnit,
                     Supplier<T> dbLoader, TypeReference<T> typeRef) {
        // 1. 先查Caffeine本地缓存（存的是JSON字符串）
        String localJson = courseLocalCache.getIfPresent(key);
        if (localJson != null) {
            log.debug("Caffeine本地缓存命中，key: {}", key);
            return deserialize(localJson, typeRef);
        }

        // 2. 查询Redis分布式缓存
        T redisValue = readFromRedis(redisKey, typeRef);
        if (redisValue != null) {
            log.debug("Redis缓存命中，key: {}", redisKey);
            // 回写Caffeine本地缓存
            courseLocalCache.put(key, serialize(redisValue));
            return redisValue;
        }

        // 3. 双层miss，加Redisson分布式锁回源DB，防止缓存击穿
        RLock lock = redissonClient.getLock("lock:cache:" + redisKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    // 双重检查：抢到锁后再查一次Redis，可能前一个线程已回源并回写
                    T doubleCheck = readFromRedis(redisKey, typeRef);
                    if (doubleCheck != null) {
                        courseLocalCache.put(key, serialize(doubleCheck));
                        return doubleCheck;
                    }
                    // 查询数据库
                    log.debug("查询数据库，key: {}", key);
                    T value = dbLoader.get();
                    if (value != null) {
                        put(key, redisKey, value, expireTime, timeUnit);
                    }
                    return value;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // 未抢到锁，说明已有其他线程在回源，短暂等待后重试读Redis
                Thread.sleep(RETRY_SLEEP_MILLIS);
                T retry = readFromRedis(redisKey, typeRef);
                if (retry != null) {
                    courseLocalCache.put(key, serialize(retry));
                    return retry;
                }
                // 仍未读到，直接查DB兜底，避免请求长时间堆积
                log.debug("未获锁且Redis仍miss，直接回源DB，key: {}", key);
                return dbLoader.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("缓存加载被中断，直接回源DB，key: {}", key);
            return dbLoader.get();
        }
    }

    /**
     * 写入双层缓存
     */
    public void put(String key, String redisKey, Object value, long expireTime, TimeUnit timeUnit) {
        String json = serialize(value);
        if (json == null) {
            return;
        }
        // 写入Caffeine本地缓存（JSON字符串）
        courseLocalCache.put(key, json);
        // 写入Redis分布式缓存
        redisTemplate.opsForValue().set(redisKey, json, expireTime, timeUnit);
    }

    /**
     * 删除双层缓存
     */
    public void evict(String key, String redisKey) {
        courseLocalCache.invalidate(key);
        redisTemplate.delete(redisKey);
        log.debug("清除缓存，localKey: {}, redisKey: {}", key, redisKey);
    }

    /**
     * 批量删除双层缓存
     */
    public void evictBatch(String[] keys, String... redisKeys) {
        courseLocalCache.invalidateAll(Arrays.asList(keys));
        redisTemplate.delete(Arrays.asList(redisKeys));
    }

    /**
     * 从Redis读取并反序列化
     */
    private <T> T readFromRedis(String redisKey, TypeReference<T> typeRef) {
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return null;
        }
        return deserialize(json, typeRef);
    }

    /**
     * 反序列化JSON字符串
     */
    @SuppressWarnings("unchecked")
    private <T> T deserialize(String json, TypeReference<T> typeRef) {
        try {
            if (typeRef != null) {
                return objectMapper.readValue(json, typeRef);
            }
            // 兼容旧调用方：反序列化为Object
            return (T) objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败，json: {}", json, e);
            return null;
        }
    }

    /**
     * 序列化为JSON字符串
     */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败，value: {}", value, e);
            return null;
        }
    }
}