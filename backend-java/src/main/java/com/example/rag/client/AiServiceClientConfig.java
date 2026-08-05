package com.example.rag.client;

import com.example.rag.security.AiInternalTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * RestTemplate 配置（调用 Python AI 服务）
 *
 * <p>配置内容：
 * <ul>
 *   <li>连接超时：5 秒</li>
 *   <li>读取超时：120 秒（AI 解析 / LLM 生成可能较慢）</li>
 *   <li>内部签名头：每个请求自动附加 HMAC-SHA256 签名头，
 *       供 Python 侧 {@code require_internal_service} 依赖校验</li>
 * </ul>
 *
 * @author knowledge-rag团队
 */
@Configuration
@RequiredArgsConstructor
public class AiServiceClientConfig {

    private final AiInternalTokenProvider aiInternalTokenProvider;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor signatureInterceptor = (request, body, execution) -> {
            String method = request.getMethod() != null
                    ? request.getMethod().name() : "GET";
            String path = request.getURI().getPath();
            Map<String, String> headers = aiInternalTokenProvider.buildSignatureHeaders(method, path);
            headers.forEach(request.getHeaders()::set);
            return execution.execute(request, body);
        };

        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .additionalInterceptors(signatureInterceptor)
                .build();
    }
}