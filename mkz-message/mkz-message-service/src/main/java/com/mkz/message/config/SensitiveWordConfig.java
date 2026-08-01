package com.mkz.message.config;

import com.mkz.message.service.ISensitiveService;
import com.mkz.message.utils.SensitiveWordDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;

@Configuration
public class SensitiveWordConfig {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ISensitiveService sensitiveService;

    @PostConstruct
    public void initSensitiveWordDetector() {
        SensitiveWordDetector.init(stringRedisTemplate, sensitiveService);
    }
}