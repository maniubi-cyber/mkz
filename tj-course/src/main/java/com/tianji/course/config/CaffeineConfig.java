package com.tianji.course.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine本地缓存配置
 * 
 * 双层缓存架构：Caffeine(本地缓存) + Redis(分布式缓存)
 * - Caffeine作为一级缓存，利用refreshAfterWrite异步刷新机制对Redis压力进行兜底
 * - 有效避免热点Key过期瞬间大量请求穿透至数据库
 * - 显著提升接口响应速度与稳定性
 */
@Configuration
public class CaffeineConfig {

    /**
     * 课程信息本地缓存
     * - 初始容量100，最大容量1000
     * - 写入后5分钟触发异步刷新
     * - 写入后10分钟过期
     */
    @Bean("courseLocalCache")
    public Cache<String, Object> courseLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                // 写入后5分钟异步刷新，对Redis压力进行兜底
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                // 写入后10分钟过期
                .expireAfterWrite(10, TimeUnit.MINUTES)
                // 开启统计
                .recordStats()
                .build();
    }

    /**
     * 课程排行榜本地缓存
     * - 初始容量50，最大容量500
     * - 写入后1分钟触发异步刷新
     * - 写入后5分钟过期
     */
    @Bean("rankingLocalCache")
    public Cache<String, Object> rankingLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(500)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 课程分类本地缓存
     * - 初始容量20，最大容量200
     * - 写入后10分钟触发异步刷新
     * - 写入后30分钟过期
     */
    @Bean("categoryLocalCache")
    public Cache<String, Object> categoryLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(20)
                .maximumSize(200)
                .refreshAfterWrite(10, TimeUnit.MINUTES)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
