package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for RAG question answering.
 */
public record RagAskRequest(
        @NotBlank(message = "queryText must not be blank")
        String queryText,
        Integer topK,
        Float minScore,
        String provider,
        String model,
        Double temperature
) {
}
