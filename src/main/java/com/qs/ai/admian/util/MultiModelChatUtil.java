package com.qs.ai.admian.util;

import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unified chat entry for Qwen and DeepSeek.
 */
@Component
@RequiredArgsConstructor
public class MultiModelChatUtil {

    private static final String DEFAULT_QWEN_MODEL = "qwen-turbo";
    private static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";

    private final AiApiUtil aiApiUtil;
    private final OllamaChatUtil ollamaChatUtil;

    public AiApiChatResult chat(String provider,
                                List<AiChatMessage> messages,
                                AiChatOptions options) {
        AiModelProvider resolvedProvider = resolveProvider(provider);
        if (resolvedProvider == AiModelProvider.OLLAMA) {
            return ollamaChatUtil.chat(messages, withDefaultModel(resolvedProvider, options));
        }
        return aiApiUtil.chat(resolvedProvider, messages, withDefaultModel(resolvedProvider, options));
    }

    public AiApiChatResult streamChat(String provider,
                                      List<AiChatMessage> messages,
                                      AiChatOptions options,
                                      Consumer<String> contentConsumer) {
        AiModelProvider resolvedProvider = resolveProvider(provider);
        if (resolvedProvider == AiModelProvider.OLLAMA) {
            return ollamaChatUtil.streamChat(messages, withDefaultModel(resolvedProvider, options), contentConsumer);
        }
        return aiApiUtil.streamChat(
                resolvedProvider,
                messages,
                withDefaultModel(resolvedProvider, options),
                contentConsumer::accept
        );
    }

    public AiModelProvider resolveProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return AiModelProvider.QWEN;
        }
        return AiModelProvider.valueOf(provider.trim().toUpperCase());
    }

    public String defaultModel(String provider) {
        return defaultModel(resolveProvider(provider));
    }

    private AiChatOptions withDefaultModel(AiModelProvider provider, AiChatOptions options) {
        AiChatOptions safeOptions = options == null ? AiChatOptions.builder().build() : options;
        return AiChatOptions.builder()
                .model(StringUtils.hasText(safeOptions.model()) ? safeOptions.model() : defaultModel(provider))
                .temperature(safeOptions.temperature())
                .maxTokens(safeOptions.maxTokens())
                .maxInputTokens(safeOptions.maxInputTokens())
                .build();
    }

    private String defaultModel(AiModelProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> DEFAULT_DEEPSEEK_MODEL;
            case OLLAMA -> ollamaChatUtil.defaultModel();
            case QWEN -> DEFAULT_QWEN_MODEL;
        };
    }
}
