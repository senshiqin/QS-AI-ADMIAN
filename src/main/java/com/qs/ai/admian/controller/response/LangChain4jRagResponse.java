package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * LangChain4j RetrievalChain demo response.
 */
public record LangChain4jRagResponse(
        String question,
        String answer,
        String provider,
        String model,
        Integer topK,
        Float minScore,
        Integer hitCount,
        List<LangChain4jRagChunkResponse> chunks,
        String promptContext
) {
}
