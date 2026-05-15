package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for RAG query rewrite.
 */
public record RagRewriteRequest(
        @NotBlank(message = "queryText must not be blank")
        String queryText,
        Boolean useLlm,
        String provider,
        String model
) {
}
