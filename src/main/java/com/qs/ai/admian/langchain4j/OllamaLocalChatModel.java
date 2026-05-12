package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.util.OllamaChatUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * LangChain4j ChatModel backed by local Ollama.
 */
@RequiredArgsConstructor
public class OllamaLocalChatModel implements ChatModel {

    private final OllamaChatUtil ollamaChatUtil;

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String model = StringUtils.hasText(request.modelName()) ? request.modelName() : ollamaChatUtil.defaultModel();
        AiApiChatResult result = ollamaChatUtil.chat(
                toAiChatMessages(request.messages()),
                AiChatOptions.builder()
                        .model(model)
                        .temperature(request.temperature())
                        .maxTokens(request.maxOutputTokens())
                        .build()
        );
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(result.answer()))
                .modelName(model)
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
}
