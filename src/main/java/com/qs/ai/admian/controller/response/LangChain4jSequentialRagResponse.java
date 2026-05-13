package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Response for retrieval -> summarization -> QA sequential chain.
 */
public record LangChain4jSequentialRagResponse(
        String conversationId,
        String question,
        String answer,
        String provider,
        String model,
        Integer topK,
        Float minScore,
        Integer hitCount,
        Integer estimatedInputTokens,
        Integer memoryTokens,
        Integer contextTokens,
        Integer summaryTokens,
        String memoryContext,
        String retrievedContextSummary,
        List<LangChain4jRagChunkResponse> chunks
) {
}
