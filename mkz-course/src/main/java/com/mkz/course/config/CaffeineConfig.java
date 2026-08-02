package com.mkz.course.config;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Bean("courseLocalCache")
    public LoadingCache<String, String> courseLocalCache() {
        return Caffeine.<String, String>newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build(cacheLoader());
    }

    private CacheLoader<String, String> cacheLoader() {
        return new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                return redisTemplate.opsForValue().get(key);
            }

            @Override
            public String reload(String key, String oldValue) {
                String newValue = redisTemplate.opsForValue().get(key);
                return newValue != null ? newValue : oldValue;
            }
        };
    }
}