package com.qs.ai.admian.service.dto;

import lombok.Builder;

/**
 * Configurable chat completion options.
 */
@Builder
public record AiChatOptions(
        String model,
        Double temperature,
        Integer maxTokens,
        Integer maxInputTokens
) {
}
