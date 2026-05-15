package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Lightweight RAG retrieval evaluation response.
 */
public record RagEvalResponse(
        String queryText,
        String rewrittenQuery,
        Integer topK,
        Integer hitCount,
        Double keywordRecall,
        Boolean expectedFileHit,
        Boolean passed,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        List<RagRerankChunkResponse> chunks
) {
}
