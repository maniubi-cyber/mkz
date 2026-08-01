package com.example.rag.client;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置（调用 Python AI 服务）
 *
 * <p>配置内容：
 * <ul>
 *   <li>连接超时：5 秒</li>
 *   <li>读取超时：120 秒（AI 解析 / LLM 生成可能较慢）</li>
 * </ul>
 *
 * @author knowledge-rag团队
 */
@Configuration
public class AiServiceClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }
}