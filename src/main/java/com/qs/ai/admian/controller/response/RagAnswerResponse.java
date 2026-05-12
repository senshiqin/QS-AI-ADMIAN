package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * RAG final answer with referenced chunks.
 */
public record RagAnswerResponse(
        String queryText,
        String answer,
        String provider,
        String model,
        Integer hitCount,
        List<RagRetrievedChunkResponse> chunks
) {
}
