package com.mkz.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 桥接服务启动类。
 * 通过 OpenFeign 声明式调用 Python AI 服务（FastAPI），将 AI 能力纳入 Spring Cloud 生态。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.mkz.bridge.feign")
@ComponentScan("com.mkz")
public class AiBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiBridgeApplication.class, args);
    }
}
