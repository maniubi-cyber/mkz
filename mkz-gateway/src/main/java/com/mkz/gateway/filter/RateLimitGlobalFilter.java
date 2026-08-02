package com.mkz.gateway.filter;

import com.mkz.gateway.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.mkz.auth.common.constants.JwtConstants.USER_HEADER;

/**
 * 网关限流过滤器（固定窗口）
 * <p>
 * 按客户端IP（已登录用户按 user-info 头中的用户id）统计窗口内请求数，超过阈值返回 429。
 * 执行顺序在鉴权过滤器（order=1000）之前，从入口保护下游服务。
 * 说明：单实例内存窗口；网关多实例部署时各自独立统计，属于网关层基础防护。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties properties;

    /** 窗口计数：key -> [窗口起点毫秒, 窗口内请求数] */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    /** 计数表超过该大小触发一次过期清理，防止无界增长 */
    private static final int MAX_WINDOW_SIZE = 10000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        // 放行路径直接通过
        for (String prefix : properties.getExcludePaths()) {
            if (path.startsWith(prefix)) {
                return chain.filter(exchange);
            }
        }
        String key = resolveKey(exchange);
        if (overLimit(key)) {
            log.warn("[网关限流] 触发限流，key={}, path={}", key, path);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    /**
     * 限流维度：已登录用户按 userId（user-info 头），未登录按客户端 IP
     */
    private String resolveKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(USER_HEADER);
        if (userId != null && !userId.isEmpty()) {
            return "u:" + userId;
        }
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String ip;
        if (forwarded != null && !forwarded.isEmpty()) {
            ip = forwarded.split(",")[0].trim();
        } else {
            InetSocketAddress address = exchange.getRequest().getRemoteAddress();
            ip = address != null && address.getAddress() != null
                    ? address.getAddress().getHostAddress() : "unknown";
        }
        return "ip:" + ip;
    }

    /**
     * 固定窗口计数：超限返回 true
     */
    private boolean overLimit(String key) {
        long now = System.currentTimeMillis();
        long windowMillis = properties.getWindowSeconds() * 1000L;
        long[] window = windows.compute(key, (k, v) -> {
            if (v == null || now - v[0] >= windowMillis) {
                // 新窗口
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });
        // 偶发清理过期窗口，防止内存无界增长
        if (windows.size() > MAX_WINDOW_SIZE) {
            cleanupExpired(now, windowMillis * 2);
        }
        return window[1] > properties.getLimit();
    }

    private void cleanupExpired(long now, long expireMillis) {
        Iterator<Map.Entry<String, long[]>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, long[]> entry = it.next();
            if (now - entry.getValue()[0] >= expireMillis) {
                it.remove();
            }
        }
    }

    @Override
    public int getOrder() {
        // 在鉴权（1000）之前执行，请求链路基础过滤器（HIGHEST_PRECEDENCE）之后
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
