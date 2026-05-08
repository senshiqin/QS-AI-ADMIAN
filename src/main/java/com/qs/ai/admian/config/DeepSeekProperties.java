package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek API configuration.
 */
@Data
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    private String apiKey;

    private String baseUrl = "https://api.deepseek.com";

    private String chatPath = "/chat/completions";

    private Integer connectTimeoutMs = 5000;

    private Integer readTimeoutMs = 60000;
}
