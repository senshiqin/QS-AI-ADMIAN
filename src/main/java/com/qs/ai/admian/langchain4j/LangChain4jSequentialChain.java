package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.config.RagProperties;
import com.qs.ai.admian.service.AiApiService;
import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.ChatContextService;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.dto.ChatContextMessage;
import com.qs.ai.admian.service.dto.SelectedAiModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sequential chain: retrieve evidence, summarize evidence, then answer with memory.
 */
@Component
@RequiredArgsConstructor
public class LangChain4jSequentialChain {

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是 RAG 检索资料压缩助手。
            只总结参考资料中的事实，保留来源编号，例如 [1]、[2]。
            不要添加资料之外的推断。
            """;
    private static final String SUMMARY_PROMPT_TEMPLATE = """
            用户问题：
            {{question}}

            检索资料：
            {{context}}

            请将检索资料压缩成不超过 {{maxSummaryChars}} 字的中文要点摘要。
            摘要必须保留和问题直接相关的事实、限制条件、冲突信息和来源编号。
            """;
    private static final String ANSWER_SYSTEM_PROMPT = """
            你是一个严格基于知识库证据回答的多轮 RAG 助手。
            规则：
            1. 优先依据“检索资料摘要”回答。
            2. “对话记忆”只用于理解上下文指代，不能替代知识库证据。
            3. 如果证据不足，直接说明“当前知识库没有足够依据回答该问题”。
            4. 涉及事实、结论、步骤时尽量标注 [1]、[2] 这样的来源编号。
            """;
    private static final String ANSWER_PROMPT_TEMPLATE = """
            对话记忆：
            {{memory}}

            用户当前问题：
            {{question}}

            检索资料摘要：
            {{summary}}

            请基于上述信息用中文回答，结构清晰、避免冗长。
            """;

    private final MilvusRetriever milvusRetriever;
    private final Map<String, ChatModel> chatModels;
    private final ChatContextService chatContextService;
    private final AiApiService aiApiService;
    private final AiModelSelectionStrategy modelSelectionStrategy;
    private final RagProperties ragProperties;

    public Result run(Options options) {
        Options safeOptions = options.withDefaults(ragProperties);
        SelectedAiModel selectedModel = modelSelectionStrategy.select(safeOptions.provider(), safeOptions.model());
        ChatModel chatModel = resolveChatModel(selectedModel.provider());

        List<Content> contents = milvusRetriever.retrieve(
                safeOptions.question(),
                safeOptions.topK(),
                safeOptions.minScore()
        );
        String retrievedContext = trimToTokenBudget(buildRetrievedContext(contents), safeOptions.maxContextTokens());
        String memoryContext = buildMemoryContext(
                safeOptions.userId(),
                safeOptions.conversationId(),
                safeOptions.maxMemoryTokens()
        );

        String summary = summarize(chatModel, selectedModel, safeOptions, retrievedContext);
        String answer = answer(chatModel, selectedModel, safeOptions, memoryContext, summary);

        if (safeOptions.saveMemory()) {
            saveMemory(safeOptions.userId(), safeOptions.conversationId(), safeOptions.question(), answer);
        }

        int memoryTokens = aiApiService.estimateTokens(memoryContext);
        int contextTokens = aiApiService.estimateTokens(retrievedContext);
        int summaryTokens = aiApiService.estimateTokens(summary);
        int inputTokens = memoryTokens + summaryTokens + aiApiService.estimateTokens(safeOptions.question()) + 16;

        return new Result(
                safeOptions.conversationId(),
                safeOptions.question(),
                answer,
                selectedModel.configKey(),
                selectedModel.model(),
                safeOptions.topK(),
                safeOptions.minScore(),
                contents,
                memoryContext,
                summary,
                inputTokens,
                memoryTokens,
                contextTokens,
                summaryTokens
        );
    }

    private String summarize(ChatModel chatModel,
                             SelectedAiModel selectedModel,
                             Options options,
                             String retrievedContext) {
        if (!StringUtils.hasText(retrievedContext)) {
            return "未检索到满足阈值的参考资料。";
        }
        String prompt = PromptTemplate.from(SUMMARY_PROMPT_TEMPLATE)
                .apply(Map.of(
                        "question", options.question(),
                        "context", retrievedContext,
                        "maxSummaryChars", String.valueOf(resolveSummaryChars(options.summaryMaxTokens()))
                ))
                .text();
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(SUMMARY_SYSTEM_PROMPT),
                        UserMessage.from(prompt)
                ))
                .modelName(selectedModel.model())
                .temperature(0.1D)
                .maxOutputTokens(options.summaryMaxTokens())
                .build());
        return response.aiMessage().text();
    }

    private String answer(ChatModel chatModel,
                          SelectedAiModel selectedModel,
                          Options options,
                          String memoryContext,
                          String summary) {
        String prompt = PromptTemplate.from(ANSWER_PROMPT_TEMPLATE)
                .apply(Map.of(
                        "memory", StringUtils.hasText(memoryContext) ? memoryContext : "无",
                        "question", options.question(),
                        "summary", StringUtils.hasText(summary) ? summary : "无"
                ))
                .text();
        List<ChatMessage> messages = List.of(
                SystemMessage.from(ANSWER_SYSTEM_PROMPT),
                UserMessage.from(prompt)
        );
        assertTokenLimit(messages, options.maxInputTokens());
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(messages)
                .modelName(selectedModel.model())
                .temperature(options.temperature())
                .maxOutputTokens(options.answerMaxTokens())
                .build());
        return response.aiMessage().text();
    }

    private String buildRetrievedContext(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            context.append("[")
                    .append(i + 1)
                    .append("] 来源: ")
                    .append(content.textSegment().metadata().getString(MilvusRetriever.META_FILE_NAME))
                    .append(" | 文件ID: ")
                    .append(content.textSegment().metadata().getLong(MilvusRetriever.META_FILE_ID))
                    .append(" | 切片: ")
                    .append(content.textSegment().metadata().getInteger(MilvusRetriever.META_CHUNK_INDEX))
                    .append(" | 相似度: ")
                    .append(toFloat(content.metadata().get(ContentMetadata.SCORE)))
                    .append(System.lineSeparator())
                    .append(content.textSegment().text())
                    .append(System.lineSeparator());
        }
        return context.toString();
    }

    private String buildMemoryContext(Long userId, String conversationId, int maxMemoryTokens) {
        if (userId == null || !StringUtils.hasText(conversationId) || maxMemoryTokens <= 0) {
            return "";
        }
        List<ChatContextMessage> messages = chatContextService.getContextMessages(userId, conversationId);
        if (messages.isEmpty()) {
            return "";
        }

        List<String> selected = new ArrayList<>();
        int tokens = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatContextMessage message = messages.get(i);
            String line = normalizeRole(message.getRole()) + ": " + message.getContent();
            int lineTokens = aiApiService.estimateTokens(line) + 4;
            if (!selected.isEmpty() && tokens + lineTokens > maxMemoryTokens) {
                break;
            }
            selected.add(0, line);
            tokens += lineTokens;
        }
        return String.join(System.lineSeparator(), selected);
    }

    private void saveMemory(Long userId, String conversationId, String question, String answer) {
        if (userId == null || !StringUtils.hasText(conversationId) || !StringUtils.hasText(answer)) {
            return;
        }
        chatContextService.addContextMessages(userId, conversationId, List.of(
                ChatContextMessage.builder()
                        .role("user")
                        .content(question)
                        .timestamp(LocalDateTime.now())
                        .build(),
                ChatContextMessage.builder()
                        .role("assistant")
                        .content(answer)
                        .timestamp(LocalDateTime.now())
                        .build()
        ));
    }

    private void assertTokenLimit(List<ChatMessage> messages, int maxInputTokens) {
        List<AiChatMessage> aiMessages = messages.stream()
                .map(this::toAiChatMessage)
                .toList();
        aiApiService.validateInputTokens(aiMessages, maxInputTokens);
    }

    private AiChatMessage toAiChatMessage(ChatMessage message) {
        return switch (message.type()) {
            case SYSTEM -> AiChatMessage.builder()
                    .role("system")
                    .content(((SystemMessage) message).text())
                    .build();
            case USER -> AiChatMessage.builder()
                    .role("user")
                    .content(((UserMessage) message).singleText())
                    .build();
            case AI -> AiChatMessage.builder()
                    .role("assistant")
                    .content(((AiMessage) message).text())
                    .build();
            default -> AiChatMessage.builder()
                    .role("user")
                    .content("")
                    .build();
        };
    }

    private String trimToTokenBudget(String text, int maxTokens) {
        if (!StringUtils.hasText(text) || maxTokens <= 0) {
            return "";
        }
        if (aiApiService.estimateTokens(text) <= maxTokens) {
            return text;
        }
        int end = text.length();
        int step = Math.max(200, text.length() / 10);
        while (end > 0 && aiApiService.estimateTokens(text.substring(0, end)) > maxTokens) {
            end = Math.max(0, end - step);
        }
        return text.substring(0, end);
    }

    private ChatModel resolveChatModel(AiModelProvider provider) {
        ChatModel chatModel = switch (provider) {
            case DEEPSEEK -> chatModels.get("deepSeekChatModel");
            case OLLAMA -> chatModels.get("ollamaChatModel");
            case QWEN -> chatModels.get("qwenChatModel");
        };
        if (chatModel == null) {
            throw new IllegalStateException("ChatModel bean not found for provider: " + provider);
        }
        return chatModel;
    }

    private String normalizeRole(String role) {
        return "assistant".equals(role) ? "助手" : "用户";
    }

    private Float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return null;
    }

    private int resolveSummaryChars(int summaryMaxTokens) {
        return Math.max(300, summaryMaxTokens * 2);
    }

    public record Options(
            Long userId,
            String conversationId,
            String question,
            String provider,
            String model,
            Double temperature,
            Integer topK,
            Float minScore,
            Integer maxInputTokens,
            Integer maxMemoryTokens,
            Integer maxContextTokens,
            Integer summaryMaxTokens,
            Integer answerMaxTokens,
            Boolean saveMemory
    ) {

        private Options withDefaults(RagProperties properties) {
            int defaultTopK = properties.getDefaultTopK() == null || properties.getDefaultTopK() <= 0
                    ? 5
                    : properties.getDefaultTopK();
            float defaultMinScore = properties.getDefaultMinScore() == null || properties.getDefaultMinScore() <= 0
                    ? 0.55F
                    : properties.getDefaultMinScore();
            int defaultMaxInputTokens = properties.getMaxInputTokens() == null || properties.getMaxInputTokens() <= 0
                    ? 6000
                    : properties.getMaxInputTokens();
            int defaultContextTokens = Math.max(1000, defaultMaxInputTokens / 2);
            int defaultMemoryTokens = Math.max(600, defaultMaxInputTokens / 5);
            int defaultAnswerTokens = properties.getAnswerMaxTokens() == null || properties.getAnswerMaxTokens() <= 0
                    ? 1200
                    : properties.getAnswerMaxTokens();
            return new Options(
                    userId,
                    conversationId,
                    question,
                    provider,
                    model,
                    temperature == null ? properties.getAnswerTemperature() : temperature,
                    topK == null || topK <= 0 ? defaultTopK : topK,
                    minScore == null || minScore <= 0 ? defaultMinScore : minScore,
                    maxInputTokens == null || maxInputTokens <= 0 ? defaultMaxInputTokens : maxInputTokens,
                    maxMemoryTokens == null || maxMemoryTokens < 0 ? defaultMemoryTokens : maxMemoryTokens,
                    maxContextTokens == null || maxContextTokens <= 0 ? defaultContextTokens : maxContextTokens,
                    summaryMaxTokens == null || summaryMaxTokens <= 0 ? 500 : summaryMaxTokens,
                    answerMaxTokens == null || answerMaxTokens <= 0 ? defaultAnswerTokens : answerMaxTokens,
                    saveMemory == null || saveMemory
            );
        }
    }

    public record Result(
            String conversationId,
            String question,
            String answer,
            String provider,
            String model,
            Integer topK,
            Float minScore,
            List<Content> contents,
            String memoryContext,
            String retrievedContextSummary,
            Integer estimatedInputTokens,
            Integer memoryTokens,
            Integer contextTokens,
            Integer summaryTokens
    ) {
    }
}
