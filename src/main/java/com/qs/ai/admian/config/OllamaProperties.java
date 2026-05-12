package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama local model configuration.
 */
@Data
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";

    private String chatPath = "/api/chat";

    private String model = "llama3.2:3b";

    private Integer connectTimeoutMs = 5000;

    private Integer readTimeoutMs = 120000;

    private Integer numPredict = 1024;

    private Integer numCtx = 4096;

    private Integer numThread;

    private String keepAlive = "10m";
}
