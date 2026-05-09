package com.qs.ai.admian.util;

import com.qs.ai.admian.config.MilvusProperties;
import com.qs.ai.admian.exception.MilvusVectorException;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.MilvusVectorService;
import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.service.dto.MilvusVectorRecord;
import com.qs.ai.admian.service.dto.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Utility facade for Milvus vector operations in RAG workflows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorUtil {

    private static final int DEFAULT_TOP_K = 5;
    private static final float DEFAULT_SIMILARITY_THRESHOLD = 0.7F;

    private final MilvusVectorService milvusVectorService;
    private final MilvusProperties milvusProperties;

    public long batchInsert(List<MilvusVectorRecord> records) {
        validateRecords(records);
        try {
            return milvusVectorService.upsertBatch(records);
        } catch (ParamException | MilvusVectorException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Milvus batch insert failed", ex);
            throw new MilvusVectorException("Milvus batch insert failed: " + ex.getMessage());
        }
    }

    public long batchInsert(List<TextChunk> chunks, List<float[]> vectors) {
        return batchInsert(toVectorRecords(chunks, vectors));
    }

    public List<MilvusSearchResult> similaritySearch(float[] queryVector) {
        return similaritySearch(queryVector, DEFAULT_TOP_K, resolveSimilarityThreshold());
    }

    public List<MilvusSearchResult> similaritySearch(float[] queryVector, int topK) {
        return similaritySearch(queryVector, topK, resolveSimilarityThreshold());
    }

    public List<MilvusSearchResult> similaritySearch(float[] queryVector, int topK, float minScore) {
        validateVector(queryVector);
        float safeMinScore = minScore <= 0 ? resolveSimilarityThreshold() : minScore;
        int safeTopK = topK <= 0 ? DEFAULT_TOP_K : topK;
        try {
            return milvusVectorService.search(queryVector, safeTopK, safeMinScore);
        } catch (ParamException | MilvusVectorException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Milvus similarity search failed", ex);
            throw new MilvusVectorException("Milvus similarity search failed: " + ex.getMessage());
        }
    }

    public long deleteByFileId(Long fileId) {
        if (fileId == null) {
            throw new ParamException("fileId must not be null");
        }
        try {
            return milvusVectorService.deleteByFileId(fileId);
        } catch (ParamException | MilvusVectorException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Milvus delete by file id failed, fileId={}", fileId, ex);
            throw new MilvusVectorException("Milvus delete by file id failed: " + ex.getMessage());
        }
    }

    public int getEmbeddingDimension() {
        Integer dimension = milvusProperties.getDimension();
        return dimension == null || dimension <= 0 ? 1024 : dimension;
    }

    public float getSimilarityThreshold() {
        return resolveSimilarityThreshold();
    }

    private List<MilvusVectorRecord> toVectorRecords(List<TextChunk> chunks, List<float[]> vectors) {
        if (chunks == null || chunks.isEmpty()) {
            throw new ParamException("chunks must not be empty");
        }
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new ParamException("vectors size must be equal to chunks size");
        }

        return IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    TextChunk chunk = chunks.get(index);
                    return new MilvusVectorRecord(
                            chunk.chunkId(),
                            chunk.fileId(),
                            chunk.chunkIndex(),
                            chunk.content(),
                            vectors.get(index)
                    );
                })
                .toList();
    }

    private void validateRecords(List<MilvusVectorRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new ParamException("vector records must not be empty");
        }
        records.forEach(record -> validateVector(record.vector()));
    }

    private void validateVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new ParamException("vector must not be empty");
        }
        if (vector.length != getEmbeddingDimension()) {
            throw new ParamException("vector dimension mismatch, expected="
                    + getEmbeddingDimension() + ", actual=" + vector.length);
        }
    }

    private float resolveSimilarityThreshold() {
        Float threshold = milvusProperties.getSimilarityThreshold();
        return threshold == null || threshold <= 0 ? DEFAULT_SIMILARITY_THRESHOLD : threshold;
    }
}
