package com.qs.ai.admian.service.dto;

import lombok.Builder;

/**
 * Parsed Qwen chat completion result.
 */
@Builder
public record QwenChatResult(
        String answer,
        String requestId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
