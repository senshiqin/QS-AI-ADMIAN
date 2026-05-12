package com.qs.ai.admian.service;

import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.AiModelsProperties;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.dto.SelectedAiModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Selects a model from explicit provider, model prefix, or configured default.
 */
@Component
@RequiredArgsConstructor
public class AiModelSelectionStrategy {

    private final AiModelConfigRegistry registry;

    public SelectedAiModel select(String requestedProvider, String requestedModel) {
        AiModelsProperties properties = registry.current();
        String configKey = resolveConfigKey(properties, requestedProvider, requestedModel);
        Map.Entry<String, AiModelsProperties.Model> entry = registry.findByKeyOrAlias(configKey)
                .orElseThrow(() -> new ParamException("AI model provider not found: " + configKey));

        if (!registry.isEnabled(entry.getValue())) {
            // 被禁用的供应商可按配置兜底到默认模型，便于平滑切流。
            if (Boolean.TRUE.equals(properties.getSelection().getFallbackToDefault())
                    && !entry.getKey().equals(properties.getSelection().getDefaultProvider())) {
                entry = registry.findByKeyOrAlias(properties.getSelection().getDefaultProvider())
                        .orElseThrow(() -> new ParamException("Default AI model provider not found"));
            } else {
                throw new ParamException("AI model provider is disabled: " + entry.getKey());
            }
        }

        AiModelsProperties.Model modelConfig = entry.getValue();
        String modelName = StringUtils.hasText(requestedModel) ? requestedModel : modelConfig.getDefaultModel();
        return new SelectedAiModel(
                entry.getKey(),
                modelConfig.getProvider(),
                modelName,
                modelConfig.getTemperature(),
                modelConfig.getMaxTokens(),
                modelConfig.getMaxInputTokens()
        );
    }

    private String resolveConfigKey(AiModelsProperties properties, String requestedProvider, String requestedModel) {
        if (StringUtils.hasText(requestedProvider)) {
            // 显式 provider 优先级最高，高于模型名前缀推断。
            return registry.findByKeyOrAlias(requestedProvider)
                    .map(Map.Entry::getKey)
                    .orElseThrow(() -> new ParamException("Unsupported AI model provider: " + requestedProvider));
        }

        if (StringUtils.hasText(requestedModel)) {
            String model = requestedModel.trim().toLowerCase(Locale.ROOT);
            // 最长前缀优先，例如 llama3 可以覆盖更宽泛的 llama 规则。
            String route = properties.getSelection().getModelPrefixRoutes().entrySet().stream()
                    .filter(entry -> model.startsWith(entry.getKey().toLowerCase(Locale.ROOT)))
                    .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                    .map(Map.Entry::getValue)
                    .orElse(null);
            if (StringUtils.hasText(route)) {
                return route;
            }
        }

        return properties.getSelection().getDefaultProvider();
    }
}
