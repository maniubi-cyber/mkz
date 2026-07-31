package com.tianji.course.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 双层缓存管理器
 * 
 * 实现 Caffeine + Redis 双层缓存架构：
 * 1. 读取时先查Caffeine本地缓存，命中则直接返回
 * 2. Caffeine未命中则查Redis分布式缓存，命中则回写Caffeine并返回
 * 3. Redis未命中则查询数据库，然后回写Redis和Caffeine
 * 
 * 利用Caffeine的refreshAfterWrite异步刷新机制对Redis压力进行兜底，
 * 有效避免热点Key过期瞬间大量请求穿透至数据库
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TwoLevelCacheManager {

    private final Cache<String, Object> courseLocalCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 获取缓存数据（双层缓存）
     *
     * @param key        缓存key
     * @param redisKey   Redis key
     * @param expireTime Redis过期时间
     * @param timeUnit   时间单位
     * @param dbLoader   数据库查询函数
     * @param <T>        数据类型
     * @return 缓存数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, String redisKey, long expireTime, TimeUnit timeUnit, Supplier<T> dbLoader) {
        // 1. 先查Caffeine本地缓存
        Object localValue = courseLocalCache.getIfPresent(key);
        if (localValue != null) {
            log.debug("Caffeine本地缓存命中，key: {}", key);
            return (T) localValue;
        }

        // 2. 查询Redis分布式缓存
        String redisValue = redisTemplate.opsForValue().get(redisKey);
        if (redisValue != null) {
            log.debug("Redis缓存命中，key: {}", redisKey);
            try {
                T value = objectMapper.readValue(redisValue, Object.class) instanceof Integer 
                        ? (T) Integer.valueOf(redisValue) 
                        : (T) objectMapper.readValue(redisValue, Object.class);
                // 回写Caffeine本地缓存
                courseLocalCache.put(key, value);
                return value;
            } catch (JsonProcessingException e) {
                log.warn("Redis缓存反序列化失败，key: {}", redisKey, e);
            }
        }

        // 3. 查询数据库
        log.debug("查询数据库，key: {}", key);
        T value = dbLoader.get();
        if (value != null) {
            // 回写Redis和Caffeine
            put(key, redisKey, value, expireTime, timeUnit);
        }
        return value;
    }

    /**
     * 写入双层缓存
     *
     * @param key        本地缓存key
     * @param redisKey   Redis key
     * @param value      缓存值
     * @param expireTime Redis过期时间
     * @param timeUnit   时间单位
     */
    public void put(String key, String redisKey, Object value, long expireTime, TimeUnit timeUnit) {
        // 写入Caffeine本地缓存
        courseLocalCache.put(key, value);
        
        // 写入Redis分布式缓存
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey, jsonValue, expireTime, timeUnit);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败，key: {}, value: {}", redisKey, value, e);
        }
    }

    /**
     * 删除双层缓存
     *
     * @param key      本地缓存key
     * @param redisKey Redis key
     */
    public void evict(String key, String redisKey) {
        // 删除Caffeine本地缓存
        courseLocalCache.invalidate(key);
        // 删除Redis缓存
        redisTemplate.delete(redisKey);
        log.debug("清除缓存，localKey: {}, redisKey: {}", key, redisKey);
    }

    /**
     * 批量删除双层缓存
     *
     * @param keys      本地缓存key数组
     * @param redisKeys Redis key数组
     */
    public void evictBatch(String[] keys, String... redisKeys) {
        courseLocalCache.invalidateAll(java.util.Arrays.asList(keys));
        redisTemplate.delete(java.util.Arrays.asList(redisKeys));
    }
}
