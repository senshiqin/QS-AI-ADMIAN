package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DashScope API configuration.
 */
@Data
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeProperties {

    private String apiKey;

    private String baseUrl = "https://dashscope.aliyuncs.com";

    private String chatPath = "/compatible-mode/v1/chat/completions";

    private Integer connectTimeoutMs = 5000;

    private Integer readTimeoutMs = 60000;
}
