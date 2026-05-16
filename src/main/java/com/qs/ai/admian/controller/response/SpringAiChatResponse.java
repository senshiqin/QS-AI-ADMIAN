package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Spring AI style chat demo response.
 */
public record SpringAiChatResponse(
        String integrationMode,
        String provider,
        String model,
        String answer,
        Integer totalTokens,
        List<String> springAiConceptMapping
) {
}
