package com.qs.ai.admian.service;

import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.service.dto.MilvusVectorRecord;

import java.util.List;

/**
 * Milvus vector storage operations.
 */
public interface MilvusVectorService {

    boolean hasCollection();

    void createCollectionIfAbsent();

    long upsert(MilvusVectorRecord record);

    long upsertBatch(List<MilvusVectorRecord> records);

    List<MilvusSearchResult> search(float[] queryVector, int topK);

    List<MilvusSearchResult> queryByFileId(Long fileId, int limit);

    long deleteByChunkId(String chunkId);

    long deleteByFileId(Long fileId);
}
