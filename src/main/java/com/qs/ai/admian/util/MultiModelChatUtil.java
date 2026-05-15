package com.qs.ai.admian.util;

import com.qs.ai.admian.metrics.AiMetricsRecorder;
import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.dto.SelectedAiModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unified chat entry for cloud providers and local Ollama.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiModelChatUtil {

    private final AiApiUtil aiApiUtil;
    private final OllamaChatUtil ollamaChatUtil;
    private final AiModelSelectionStrategy modelSelectionStrategy;
    private final AiMetricsRecorder aiMetricsRecorder;

    public AiApiChatResult chat(String provider,
                                List<AiChatMessage> messages,
                                AiChatOptions options) {
        SelectedAiModel selected = modelSelectionStrategy.select(provider, options == null ? null : options.model());
        long startTime = System.currentTimeMillis();
        try {
            AiApiChatResult result = selected.provider() == AiModelProvider.OLLAMA
                    ? ollamaChatUtil.chat(messages, withSelectedOptions(selected, options))
                    : aiApiUtil.chat(selected.provider(), messages, withSelectedOptions(selected, options));
            log.info("LLM chat completed, provider={}, mode=normal, model={}, durationMs={}",
                    selected.provider(), selected.model(), System.currentTimeMillis() - startTime);
            aiMetricsRecorder.recordChat(selected.provider().name(), "normal", true,
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (RuntimeException ex) {
            log.warn("LLM chat failed, provider={}, mode=normal, model={}, durationMs={}",
                    selected.provider(), selected.model(), System.currentTimeMillis() - startTime, ex);
            aiMetricsRecorder.recordChat(selected.provider().name(), "normal", false,
                    System.currentTimeMillis() - startTime);
            throw ex;
        }
    }

    public AiApiChatResult streamChat(String provider,
                                      List<AiChatMessage> messages,
                                      AiChatOptions options,
                                      Consumer<String> contentConsumer) {
        SelectedAiModel selected = modelSelectionStrategy.select(provider, options == null ? null : options.model());
        long startTime = System.currentTimeMillis();
        try {
            AiApiChatResult result = selected.provider() == AiModelProvider.OLLAMA
                    ? ollamaChatUtil.streamChat(messages, withSelectedOptions(selected, options), contentConsumer)
                    : aiApiUtil.streamChat(
                    selected.provider(),
                    messages,
                    withSelectedOptions(selected, options),
                    contentConsumer::accept
            );
            log.info("LLM chat completed, provider={}, mode=stream, model={}, durationMs={}",
                    selected.provider(), selected.model(), System.currentTimeMillis() - startTime);
            aiMetricsRecorder.recordChat(selected.provider().name(), "stream", true,
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (RuntimeException ex) {
            log.warn("LLM chat failed, provider={}, mode=stream, model={}, durationMs={}",
                    selected.provider(), selected.model(), System.currentTimeMillis() - startTime, ex);
            aiMetricsRecorder.recordChat(selected.provider().name(), "stream", false,
                    System.currentTimeMillis() - startTime);
            throw ex;
        }
    }

    public AiModelProvider resolveProvider(String provider) {
        return modelSelectionStrategy.select(provider, null).provider();
    }

    public String defaultModel(String provider) {
        return modelSelectionStrategy.select(provider, null).model();
    }

    private AiChatOptions withSelectedOptions(SelectedAiModel selected, AiChatOptions options) {
        AiChatOptions safeOptions = options == null ? AiChatOptions.builder().build() : options;
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
