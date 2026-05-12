package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.config.RagProperties;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiKnowledgeFileService;
import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.util.MilvusVectorUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * LangChain4j ContentRetriever backed by Milvus.
 */
@Component
@RequiredArgsConstructor
public class MilvusRetriever implements ContentRetriever {

    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_FILE_ID = "fileId";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_FILE_NAME = "fileName";
    public static final String META_FILE_TYPE = "fileType";
    public static final String META_STORAGE_PATH = "storagePath";
    public static final String META_KB_CODE = "kbCode";

    private final EmbeddingModel qwenEmbeddingModel;
    private final MilvusVectorUtil milvusVectorUtil;
    private final AiKnowledgeFileService aiKnowledgeFileService;
    private final RagProperties ragProperties;

    @Override
    public List<Content> retrieve(Query query) {
        return retrieve(query.text(), resolveDefaultTopK(), resolveDefaultMinScore());
    }

    public List<Content> retrieve(String queryText, int topK, float minScore) {
        if (!StringUtils.hasText(queryText)) {
            throw new ParamException("queryText must not be blank");
        }
        Embedding queryEmbedding = qwenEmbeddingModel.embed(queryText).content();
        List<MilvusSearchResult> searchResults = milvusVectorUtil.similaritySearch(
                queryEmbedding.vector(),
                topK <= 0 ? resolveDefaultTopK() : topK,
                minScore <= 0 ? resolveDefaultMinScore() : minScore
        );
        Map<Long, AiKnowledgeFile> fileMap = loadFileMap(searchResults);
        return searchResults.stream()
                .map(result -> toContent(result, fileMap.get(result.fileId())))
                .toList();
    }

    private Content toContent(MilvusSearchResult result, AiKnowledgeFile file) {
        Metadata metadata = new Metadata()
                .put(META_CHUNK_ID, result.chunkId())
                .put(META_FILE_ID, result.fileId() == null ? 0L : result.fileId())
                .put(META_CHUNK_INDEX, result.chunkIndex() == null ? 0 : result.chunkIndex());
        if (file != null) {
            metadata.put(META_FILE_NAME, nullToEmpty(file.getFileName()));
            metadata.put(META_FILE_TYPE, nullToEmpty(file.getFileType()));
            metadata.put(META_STORAGE_PATH, nullToEmpty(file.getStoragePath()));
            metadata.put(META_KB_CODE, nullToEmpty(file.getKbCode()));
        }
        return Content.from(
                TextSegment.from(result.content(), metadata),
                Map.of(ContentMetadata.SCORE, result.score() == null ? 0F : result.score())
        );
    }

    private Map<Long, AiKnowledgeFile> loadFileMap(List<MilvusSearchResult> searchResults) {
        List<Long> fileIds = searchResults.stream()
                .map(MilvusSearchResult::fileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return aiKnowledgeFileService.listByIds(fileIds).stream()
                .collect(Collectors.toMap(AiKnowledgeFile::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private int resolveDefaultTopK() {
        Integer value = ragProperties.getDefaultTopK();
        return value == null || value <= 0 ? 5 : value;
    }

    private float resolveDefaultMinScore() {
        Float value = ragProperties.getDefaultMinScore();
        return value == null || value <= 0 ? milvusVectorUtil.getSimilarityThreshold() : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
