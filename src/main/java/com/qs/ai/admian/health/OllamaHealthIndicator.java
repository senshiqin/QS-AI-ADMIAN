package com.qs.ai.admian.health;

import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.AiModelsProperties;
import com.qs.ai.admian.service.dto.AiModelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Lightweight Actuator health indicator for Ollama configuration.
 */
@Component("ollama")
@RequiredArgsConstructor
public class OllamaHealthIndicator implements HealthIndicator {

    private final AiModelConfigRegistry modelConfigRegistry;

    @Override
    public Health health() {
        return modelConfigRegistry.findByProvider(AiModelProvider.OLLAMA)
                .filter(entry -> modelConfigRegistry.isEnabled(entry.getValue()))
                .map(entry -> up(entry.getValue()))
                .orElseGet(() -> Health.unknown()
                        .withDetail("enabled", false)
                        .withDetail("message", "Ollama provider is disabled or not configured")
                        .build());
    }

    private Health up(AiModelsProperties.Model model) {
        return Health.up()
                .withDetail("baseUrl", model.getBaseUrl())
                .withDetail("model", model.getDefaultModel())
                .build();
    }
}
