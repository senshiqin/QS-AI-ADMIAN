package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.LangChain4jChatRequest;
import com.qs.ai.admian.controller.request.LangChain4jEmbedRequest;
import com.qs.ai.admian.controller.request.LangChain4jRagRequest;
import com.qs.ai.admian.controller.response.LangChain4jChatResponse;
import com.qs.ai.admian.controller.response.LangChain4jEmbedResponse;
import com.qs.ai.admian.controller.response.LangChain4jRagChunkResponse;
import com.qs.ai.admian.controller.response.LangChain4jRagResponse;
import com.qs.ai.admian.config.RagProperties;
import com.qs.ai.admian.langchain4j.LangChain4jRetrievalChain;
import com.qs.ai.admian.langchain4j.MilvusRetriever;
import com.qs.ai.admian.util.response.ApiResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j demo APIs.
 */
@Tag(name = "AI LangChain4j", description = "LangChain4j embedding and chat demo APIs")
@RestController
@RequestMapping("/api/v1/ai/langchain4j")
@RequiredArgsConstructor
public class LangChain4jDemoController {

    private static final int PREVIEW_SIZE = 8;

    private final EmbeddingModel qwenEmbeddingModel;
    private final ChatModel qwenChatModel;
    private final LangChain4jRetrievalChain langChain4jRetrievalChain;
    private final RagProperties ragProperties;

    @Operation(summary = "Vectorize text using LangChain4j EmbeddingModel")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/embed")
    public ApiResponse<LangChain4jEmbedResponse> embed(@Valid @RequestBody LangChain4jEmbedRequest request) {
        Embedding embedding = qwenEmbeddingModel.embed(request.text()).content();
        return ApiResponse.success(new LangChain4jEmbedResponse(
                qwenEmbeddingModel.modelName(),
                embedding.dimension(),
                preview(embedding.vectorAsList())
        ));
    }

    @Operation(summary = "Chat with Qwen using LangChain4j ChatModel and PromptTemplate")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/chat")
    public ApiResponse<LangChain4jChatResponse> chat(@Valid @RequestBody LangChain4jChatRequest request) {
        String userPrompt = PromptTemplate.from("用户问题：{{message}}")
                .apply(Map.of("message", request.message()))
                .text();

        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(buildMessages(request.systemPrompt(), userPrompt));
        if (StringUtils.hasText(request.model())) {
            builder.modelName(request.model());
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }

        ChatResponse response = qwenChatModel.chat(builder.build());
        return ApiResponse.success(new LangChain4jChatResponse(
                response.aiMessage().text(),
                response.modelName(),
                response.tokenUsage() == null ? null : response.tokenUsage().inputTokenCount(),
                response.tokenUsage() == null ? null : response.tokenUsage().outputTokenCount(),
                response.tokenUsage() == null ? null : response.tokenUsage().totalTokenCount()
        ));
    }

    @Operation(summary = "Run RetrievalChain: Milvus retrieve, PromptTemplate compose and Qwen generate")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/rag")
    public ApiResponse<LangChain4jRagResponse> rag(@Valid @RequestBody LangChain4jRagRequest request) {
        LangChain4jRetrievalChain.Options options = new LangChain4jRetrievalChain.Options(
                request.topK(),
                request.minScore(),
                request.model(),
                request.temperature(),
                request.maxTokens()
        );
        LangChain4jRetrievalChain.Result result = langChain4jRetrievalChain.run(request.question(), options);
        int topK = request.topK() == null || request.topK() <= 0 ? ragProperties.getDefaultTopK() : request.topK();
        float minScore = request.minScore() == null || request.minScore() <= 0
                ? ragProperties.getDefaultMinScore()
                : request.minScore();
        return ApiResponse.success("LangChain4j RAG chain completed", new LangChain4jRagResponse(
                result.question(),
                result.answer(),
                result.chatResponse().modelName(),
                topK,
                minScore,
                result.contents().size(),
                result.contents().stream()
                        .map(this::toChunkResponse)
                        .toList(),
                result.context()
        ));
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildMessages(String systemPrompt, String userPrompt) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userPrompt));
        return messages;
    }

    private List<Float> preview(List<Float> vector) {
        return vector.stream()
                .limit(PREVIEW_SIZE)
                .toList();
    }

    private LangChain4jRagChunkResponse toChunkResponse(Content content) {
        return new LangChain4jRagChunkResponse(
                content.textSegment().metadata().getString(MilvusRetriever.META_CHUNK_ID),
                content.textSegment().metadata().getLong(MilvusRetriever.META_FILE_ID),
                content.textSegment().metadata().getInteger(MilvusRetriever.META_CHUNK_INDEX),
                toFloat(content.metadata().get(ContentMetadata.SCORE)),
                content.textSegment().metadata().getString(MilvusRetriever.META_FILE_NAME),
                content.textSegment().metadata().getString(MilvusRetriever.META_FILE_TYPE),
                content.textSegment().metadata().getString(MilvusRetriever.META_STORAGE_PATH),
                content.textSegment().metadata().getString(MilvusRetriever.META_KB_CODE),
                content.textSegment().text()
        );
    }

    private Float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return null;
    }
}
