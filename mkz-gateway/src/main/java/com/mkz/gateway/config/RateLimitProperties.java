package com.mkz.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关限流配置
 * <p>
 * 简单固定窗口限流：按客户端IP（已登录用户按用户id）统计窗口内请求数，超过阈值返回 429。
 * 单实例内存窗口；多实例部署时各自独立统计，精确分布式限流可替换为 Sentinel 等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mkz.gateway.rate-limit")
public class RateLimitProperties {

    /** 是否启用网关限流 */
    private boolean enabled = true;

    /** 单个窗口内单个维度（IP/用户）最大请求数 */
    private int limit = 300;

    /** 窗口时长（秒） */
    private int windowSeconds = 60;

    /** 放行路径前缀（如认证登录、websocket 握手等） */
    private List<String> excludePaths = new ArrayList<>();
}
