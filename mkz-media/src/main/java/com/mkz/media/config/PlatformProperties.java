package com.mkz.media.config;

import com.mkz.media.enums.Platform;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mkz.platform")
public class PlatformProperties {
    private Platform file;
    private Platform media;
}
