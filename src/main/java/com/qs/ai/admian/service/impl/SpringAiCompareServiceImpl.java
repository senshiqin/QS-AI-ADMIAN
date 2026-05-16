package com.qs.ai.admian.service.impl;

import com.qs.ai.admian.controller.request.SpringAiChatRequest;
import com.qs.ai.admian.controller.response.SpringAiChatResponse;
import com.qs.ai.admian.controller.response.SpringAiCompareResponse;
import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.SpringAiCompareService;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.SelectedAiModel;
import com.qs.ai.admian.util.MultiModelChatUtil;
import com.qs.ai.admian.util.TextParseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight Spring AI style adapter built on the project's existing model gateway.
 */
@Service
@RequiredArgsConstructor
public class SpringAiCompareServiceImpl implements SpringAiCompareService {

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a concise Java AI application assistant.";

    private final MultiModelChatUtil multiModelChatUtil;
    private final AiModelSelectionStrategy modelSelectionStrategy;

    @Override
    public SpringAiCompareResponse compare() {
        return new SpringAiCompareResponse(
                "Spring Boot 3.2.5",
                "Spring AI 1.x officially targets Spring Boot 3.4.x and 3.5.x, so this project keeps a lightweight adapter instead of adding Spring AI dependencies directly.",
                "Use the existing MultiModelChatUtil, RagService and MilvusVectorUtil as Spring AI style boundaries.",
                List.of(
                        new SpringAiCompareResponse.CompareItem(
                                "ChatClient",
                                "MultiModelChatUtil routes Qwen, DeepSeek and Ollama through one entry.",
                                "ChatClient provides a fluent API for prompt, options, advisors and model calls.",
                                "Both solve model access abstraction; the current project adds provider routing and runtime model config."
                        ),
                        new SpringAiCompareResponse.CompareItem(
                                "Prompt",
                                "AiChatMessage and AiChatOptions carry system/user messages and model options.",
                                "Prompt, UserMessage, SystemMessage and ChatOptions are first-class concepts.",
                                "Prompt assembly is already separated from provider HTTP details."
                        ),
                        new SpringAiCompareResponse.CompareItem(
                                "VectorStore",
                                "MilvusVectorUtil and MilvusVectorService manage vector upsert, search and delete.",
                                "VectorStore hides specific vector database implementations behind one interface.",
                                "The current Milvus layer can be migrated to Spring AI VectorStore later."
                        ),
                        new SpringAiCompareResponse.CompareItem(
                                "RAG Advisor",
                                "RagService retrieves chunks, builds context and calls LLM; RagEnhanceService adds rewrite, rerank and eval.",
                                "QuestionAnswerAdvisor can inject retrieved context into model calls.",
                                "The project already owns the RAG orchestration, so Spring AI can be introduced gradually."
                        ),
                        new SpringAiCompareResponse.CompareItem(
                                "Observability",
                                "Micrometer metrics and key timing logs cover LLM, RAG, Redis, DB, Embedding and Milvus.",
                                "Spring AI integrates with Micrometer observations for model calls.",
                                "The project can explain AI latency by segment, not only by endpoint response time."
                        )
                ),
                List.of(
                        "Upgrade Spring Boot to 3.4.x or 3.5.x before introducing Spring AI 1.x starters.",
                        "Map MultiModelChatUtil to ChatClient and keep provider routing as a custom model selection layer.",
                        "Map MilvusVectorUtil to Spring AI VectorStore while preserving existing Milvus collection schema.",
                        "Move RAG context injection into Advisor style components only after current RAG tests are stable.",
                        "Keep existing metrics names during migration so pressure-test reports remain comparable."
                )
        );
    }

    @Override
    public SpringAiChatResponse chat(SpringAiChatRequest request) {
        String cleanPrompt = TextParseUtil.cleanText(request.prompt());
        String systemPrompt = StringUtils.hasText(request.systemPrompt())
                ? TextParseUtil.cleanText(request.systemPrompt())
                : DEFAULT_SYSTEM_PROMPT;
        SelectedAiModel selected = modelSelectionStrategy.select(request.provider(), request.model());
        AiApiChatResult result = multiModelChatUtil.chat(
                selected.configKey(),
                buildMessages(systemPrompt, cleanPrompt),
                AiChatOptions.builder()
                        .model(selected.model())
                        .temperature(request.temperature() == null ? selected.temperature() : request.temperature())
                        .maxTokens(selected.maxTokens())
                        .maxInputTokens(selected.maxInputTokens())
                        .build()
        );

        return new SpringAiChatResponse(
                "Spring AI style adapter on existing MultiModelChatUtil",
                selected.provider().name(),
                selected.model(),
                result.answer(),
                result.totalTokens(),
                List.of(
                        "ChatClient.prompt() -> SpringAiChatRequest.prompt",
                        "SystemMessage -> SpringAiChatRequest.systemPrompt",
                        "ChatOptions -> AiChatOptions",
                        "Model routing -> AiModelSelectionStrategy",
                        "Model call -> MultiModelChatUtil.chat"
                )
        );
    }

    private List<AiChatMessage> buildMessages(String systemPrompt, String prompt) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.builder()
                .role("system")
                .content(systemPrompt)
                .build());
        messages.add(AiChatMessage.builder()
                .role("user")
                .content(prompt)
                .build());
        return messages;
    }
}
