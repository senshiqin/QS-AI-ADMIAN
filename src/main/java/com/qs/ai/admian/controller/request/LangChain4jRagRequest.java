package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * LangChain4j RetrievalChain demo request.
 */
public record LangChain4jRagRequest(
        @NotBlank(message = "question must not be blank")
        String question,
        String provider,
        Integer topK,
        Float minScore,
        String model,
        Double temperature,
        Integer maxTokens
) {
}
