package com.qs.ai.admian.service.dto;

/**
 * Model selected for one AI request.
 */
public record SelectedAiModel(
        String configKey,
        AiModelProvider provider,
        String model,
        Double temperature,
        Integer maxTokens,
        Integer maxInputTokens
) {
}
