package com.qs.ai.admian.controller.response;

/**
 * LangChain4j chat demo response.
 */
public record LangChain4jChatResponse(
        String answer,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
