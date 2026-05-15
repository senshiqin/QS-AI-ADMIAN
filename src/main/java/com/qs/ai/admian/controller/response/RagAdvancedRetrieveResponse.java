package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Query rewrite and rerank retrieval response.
 */
public record RagAdvancedRetrieveResponse(
        String originalQuery,
        String rewrittenQuery,
        Boolean rewriteUsed,
        Integer topK,
        Integer candidateTopK,
        Float minScore,
        Integer hitCount,
        List<RagRerankChunkResponse> chunks,
        String ragContext
) {
}
