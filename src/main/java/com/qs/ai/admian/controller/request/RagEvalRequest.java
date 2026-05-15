package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request for lightweight RAG retrieval evaluation.
 */
public record RagEvalRequest(
        @NotBlank(message = "queryText must not be blank")
        String queryText,
        List<String> expectedKeywords,
        String expectedFileName,
        Integer topK,
        Float minScore,
        Boolean rewrite,
        Boolean rewriteWithLlm,
        String provider,
        String model
) {
}
