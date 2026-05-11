package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * RAG retrieval response.
 */
public record RagRetrieveResponse(
        String queryText,
        Integer topK,
        Float minScore,
        String embeddingModel,
        Integer embeddingDimension,
        Integer hitCount,
        List<RagRetrievedChunkResponse> chunks,
        String ragContext
) {
}
