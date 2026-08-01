package com.mkz.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mkz.interests")
public class InterestsProperties {
    private int topNumber;
}
