package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.config.RagProperties;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Simple RetrievalChain-style orchestration using LangChain4j primitives.
 */
@Component
@RequiredArgsConstructor
public class LangChain4jRetrievalChain {

    private static final String DEFAULT_MODEL = "qwen-turbo";
    private static final String SYSTEM_PROMPT = """
            你是一个严格基于知识库证据回答的 RAG 助手。
            只能依据参考资料回答；如果参考资料不足，必须明确说明“当前知识库没有足够依据回答该问题”。
            回答事实、步骤、结论时尽量使用 [1]、[2] 这样的引用编号。
            """;
    private static final String USER_PROMPT_TEMPLATE = """
            用户问题：
            {{question}}

            参考资料：
            {{context}}

            请基于参考资料用中文回答，不要编造参考资料之外的内容。
            """;

    private final MilvusRetriever milvusRetriever;
    private final ChatModel qwenChatModel;
    private final RagProperties ragProperties;

    public Result run(String question, Options options) {
        Options safeOptions = options == null ? Options.defaults(ragProperties) : options.withDefaults(ragProperties);
        List<Content> contents = milvusRetriever.retrieve(question, safeOptions.topK(), safeOptions.minScore());
        String context = buildContext(contents);
        String prompt = PromptTemplate.from(USER_PROMPT_TEMPLATE)
                .apply(Map.of(
                        "question", question,
                        "context", StringUtils.hasText(context) ? context : "未检索到满足阈值的参考资料。"
                ))
                .text();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(prompt)
                ))
                .modelName(safeOptions.model())
                .temperature(safeOptions.temperature())
                .maxOutputTokens(safeOptions.maxTokens())
                .build();
        ChatResponse chatResponse = qwenChatModel.chat(chatRequest);
        return new Result(question, chatResponse.aiMessage().text(), contents, context, chatResponse);
    }

    private String buildContext(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            String block = "["
                    + (i + 1)
                    + "] 来源: "
                    + content.textSegment().metadata().getString(MilvusRetriever.META_FILE_NAME)
                    + " | 文件ID: "
                    + content.textSegment().metadata().getLong(MilvusRetriever.META_FILE_ID)
                    + " | 切片: "
                    + content.textSegment().metadata().getInteger(MilvusRetriever.META_CHUNK_INDEX)
                    + System.lineSeparator()
                    + content.textSegment().text()
                    + System.lineSeparator();
            if (context.length() + block.length() > resolveMaxContextChars()) {
                break;
            }
            context.append(block);
        }
        return context.toString();
    }

    private int resolveMaxContextChars() {
        Integer value = ragProperties.getMaxContextChars();
        return value == null || value <= 0 ? 6000 : value;
    }

    public record Options(
            Integer topK,
            Float minScore,
            String model,
            Double temperature,
            Integer maxTokens
    ) {

        private static Options defaults(RagProperties properties) {
            return new Options(
                    properties.getDefaultTopK(),
                    properties.getDefaultMinScore(),
                    DEFAULT_MODEL,
                    properties.getAnswerTemperature(),
                    properties.getAnswerMaxTokens()
            );
        }

        private Options withDefaults(RagProperties properties) {
            Options defaults = defaults(properties);
            return new Options(
                    topK == null || topK <= 0 ? defaults.topK() : topK,
                    minScore == null || minScore <= 0 ? defaults.minScore() : minScore,
                    StringUtils.hasText(model) ? model : defaults.model(),
                    temperature == null ? defaults.temperature() : temperature,
                    maxTokens == null || maxTokens <= 0 ? defaults.maxTokens() : maxTokens
            );
        }
    }

    public record Result(
            String question,
            String answer,
            List<Content> contents,
            String context,
            ChatResponse chatResponse
    ) {
    }
}
