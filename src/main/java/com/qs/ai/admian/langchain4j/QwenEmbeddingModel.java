package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.util.AiEmbeddingUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * LangChain4j EmbeddingModel backed by DashScope Qwen embeddings.
 */
@RequiredArgsConstructor
public class QwenEmbeddingModel implements EmbeddingModel {

    private final AiEmbeddingUtil aiEmbeddingUtil;
    private final int dimension;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .toList();
        List<Embedding> embeddings = aiEmbeddingUtil.embedBatchAsEmbeddings(texts);
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return aiEmbeddingUtil.getEmbeddingModel();
    }
}
