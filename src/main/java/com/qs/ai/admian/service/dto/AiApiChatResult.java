package com.qs.ai.admian.service.dto;

import lombok.Builder;

/**
 * Generic AI chat completion result.
 */
@Builder
public record AiApiChatResult(
        String answer,
        String requestId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String rawResponse
) {
}
