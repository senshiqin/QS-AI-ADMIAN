package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG retrieval and prompt configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Integer defaultChunkSize = 800;

    private Double defaultOverlapRatio = 0.15D;

    private Integer defaultTopK = 5;

    private Float defaultMinScore = 0.55F;

    private Integer candidateMultiplier = 3;

    private Integer maxContextChars = 6000;

    private Integer minChunkChars = 20;

    private Integer answerMaxTokens = 1200;

    private Integer maxInputTokens = 6000;

    private Double answerTemperature = 0.2D;
}
