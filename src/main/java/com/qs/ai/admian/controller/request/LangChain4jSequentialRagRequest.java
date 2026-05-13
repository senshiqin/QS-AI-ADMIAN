package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for retrieval -> summarization -> QA sequential chain.
 */
public record LangChain4jSequentialRagRequest(
        @NotBlank(message = "question must not be blank")
        String question,
        String conversationId,
        String provider,
        String model,
        Double temperature,
        Integer topK,
        Float minScore,
        Integer maxInputTokens,
        Integer maxMemoryTokens,
        Integer maxContextTokens,
        Integer summaryMaxTokens,
        Integer answerMaxTokens,
        Boolean saveMemory
) {
}
