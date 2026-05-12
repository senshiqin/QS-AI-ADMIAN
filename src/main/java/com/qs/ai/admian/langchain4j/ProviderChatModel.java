package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.dto.SelectedAiModel;
import com.qs.ai.admian.util.AiApiUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Base LangChain4j ChatModel backed by the existing AI API utility.
 */
@RequiredArgsConstructor
public class ProviderChatModel implements ChatModel {

    private static final double DEFAULT_TEMPERATURE = 0.7D;
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final int DEFAULT_MAX_INPUT_TOKENS = 4000;

    private final AiApiUtil aiApiUtil;
    private final AiModelSelectionStrategy modelSelectionStrategy;
    private final AiModelProvider provider;

    @Override
    public ChatResponse doChat(ChatRequest request) {
        SelectedAiModel selectedModel = modelSelectionStrategy.select(provider.name(), request.modelName());
        String model = selectedModel.model();
        Double temperature = request.temperature() == null
                ? defaultDouble(selectedModel.temperature(), DEFAULT_TEMPERATURE)
                : request.temperature();
        Integer maxTokens = request.maxOutputTokens() == null
                ? defaultInt(selectedModel.maxTokens(), DEFAULT_MAX_TOKENS)
                : request.maxOutputTokens();

        AiApiChatResult result = aiApiUtil.chat(
                provider,
                toAiChatMessages(request.messages()),
                AiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .maxInputTokens(defaultInt(selectedModel.maxInputTokens(), DEFAULT_MAX_INPUT_TOKENS))
                        .build()
        );

        return ChatResponse.builder()
                .aiMessage(AiMessage.from(result.answer()))
                .id(result.requestId())
                .modelName(model)
                .tokenUsage(new TokenUsage(result.promptTokens(), result.completionTokens(), result.totalTokens()))
                .build();
    }

    private List<com.qs.ai.admian.service.dto.AiChatMessage> toAiChatMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(this::toAiChatMessage)
                .toList();
    }

    private com.qs.ai.admian.service.dto.AiChatMessage toAiChatMessage(ChatMessage message) {
        return switch (message.type()) {
            case SYSTEM -> com.qs.ai.admian.service.dto.AiChatMessage.builder()
                    .role("system")
                    .content(((SystemMessage) message).text())
                    .build();
            case USER -> com.qs.ai.admian.service.dto.AiChatMessage.builder()
                    .role("user")
                    .content(((UserMessage) message).singleText())
                    .build();
            case AI -> com.qs.ai.admian.service.dto.AiChatMessage.builder()
                    .role("assistant")
                    .content(((AiMessage) message).text())
                    .build();
            default -> throw new IllegalArgumentException("Unsupported LangChain4j message type: " + message.type());
        };
    }

    private Integer defaultInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private Double defaultDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }
}
