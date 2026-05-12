package com.qs.ai.admian.config;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Snapshot metadata for the active AI model configuration.
 */
public record AiModelConfigState(
        long version,
        LocalDateTime refreshedAt,
        AiModelsProperties.Selection selection,
        Map<String, AiModelsProperties.Model> providers
) {
}
