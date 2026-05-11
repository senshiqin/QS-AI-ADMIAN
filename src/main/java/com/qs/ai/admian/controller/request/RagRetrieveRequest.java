package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for RAG retrieval.
 */
public record RagRetrieveRequest(
        @NotBlank(message = "queryText must not be blank")
        String queryText,
        Integer topK,
        Float minScore
) {
}
