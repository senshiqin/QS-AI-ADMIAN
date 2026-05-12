package com.qs.ai.admian.util;

import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.dto.SelectedAiModel;
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

    private final AiApiUtil aiApiUtil;
    private final OllamaChatUtil ollamaChatUtil;
    private final AiModelSelectionStrategy modelSelectionStrategy;

    public AiApiChatResult chat(String provider,
                                List<AiChatMessage> messages,
                                AiChatOptions options) {
        SelectedAiModel selected = modelSelectionStrategy.select(provider, options == null ? null : options.model());
        // 本项目中的 Ollama 不是 OpenAI-compatible 请求格式，需要走独立适配器。
        if (selected.provider() == AiModelProvider.OLLAMA) {
            return ollamaChatUtil.chat(messages, withSelectedOptions(selected, options));
        }
        return aiApiUtil.chat(selected.provider(), messages, withSelectedOptions(selected, options));
    }

    public AiApiChatResult streamChat(String provider,
                                      List<AiChatMessage> messages,
                                      AiChatOptions options,
                                      Consumer<String> contentConsumer) {
        SelectedAiModel selected = modelSelectionStrategy.select(provider, options == null ? null : options.model());
        // 云端供应商共用 OpenAI-compatible SSE 解析，本地 Ollama 使用换行 JSON 流。
        if (selected.provider() == AiModelProvider.OLLAMA) {
            return ollamaChatUtil.streamChat(messages, withSelectedOptions(selected, options), contentConsumer);
        }
        return aiApiUtil.streamChat(
                selected.provider(),
                messages,
                withSelectedOptions(selected, options),
                contentConsumer::accept
        );
    }

    public AiModelProvider resolveProvider(String provider) {
        return modelSelectionStrategy.select(provider, null).provider();
    }

    public String defaultModel(String provider) {
        return modelSelectionStrategy.select(provider, null).model();
    }

    private AiChatOptions withSelectedOptions(SelectedAiModel selected, AiChatOptions options) {
        AiChatOptions safeOptions = options == null ? AiChatOptions.builder().build() : options;
        // 请求级参数只覆盖本次调用，不修改运行时配置快照。
        return AiChatOptions.builder()
                .model(StringUtils.hasText(safeOptions.model()) ? safeOptions.model() : selected.model())
                .temperature(safeOptions.temperature() == null ? selected.temperature() : safeOptions.temperature())
                .maxTokens(safeOptions.maxTokens() == null ? selected.maxTokens() : safeOptions.maxTokens())
                .maxInputTokens(safeOptions.maxInputTokens() == null
                        ? selected.maxInputTokens()
                        : safeOptions.maxInputTokens())
                .build();
    }
}
