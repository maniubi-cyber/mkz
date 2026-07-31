package com.example.rag.client;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign Client 配置
 *
 * <p>配置内容：
 * <ul>
 *   <li>日志级别：full（记录请求和响应详情）</li>
 *   <li>重试策略：不重试（AI 服务调用失败不应重试，由上层处理）</li>
 *   <li>错误解码：自定义错误处理</li>
 * </ul>
 *
 * @author knowledge-rag团队
 */
@Slf4j
@Configuration
public class AiServiceClientConfig {

    /**
     * Feign 日志级别
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * 重试策略：不重试（AI 服务调用失败由上层业务处理）
     */
    @Bean
    Retryer feignRetryer() {
        // 初始间隔 100ms，最大间隔 1s，最多重试 1 次（即不重试）
        return new Retryer.Default(100, 1000, 1);
    }

    /**
     * 请求拦截器：添加通用请求头
     */
    @Bean
    RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Content-Type", "application/json");
            requestTemplate.header("Accept", "application/json");
        };
    }

    /**
     * 错误解码器：处理非 2xx 响应
     */
    @Bean
    ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            String errorMsg = String.format("AI 服务调用失败: method=%s, status=%d, reason=%s",
                    methodKey, response.status(), response.reason());
            log.error(errorMsg);
            return new RuntimeException(errorMsg);
        };
    }
}
