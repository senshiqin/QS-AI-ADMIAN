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

    private String embeddingPath = "/compatible-mode/v1/embeddings";

    private String embeddingModel = "text-embedding-v4";

    private Integer embeddingBatchSize = 10;

    private Integer embeddingDimensions = 1024;

    private Integer connectTimeoutMs = 5000;

    private Integer readTimeoutMs = 60000;
}
