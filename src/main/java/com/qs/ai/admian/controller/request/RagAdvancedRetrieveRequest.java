package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for query rewrite and rerank retrieval.
 */
public record RagAdvancedRetrieveRequest(
        @NotBlank(message = "queryText must not be blank")
        String queryText,
        Integer topK,
        Float minScore,
        Boolean rewrite,
        Boolean rewriteWithLlm,
        String provider,
        String model,
        Integer candidateTopK
) {
}
