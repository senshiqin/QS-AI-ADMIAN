package com.qs.ai.admian.service.dto;

/**
 * Vector record persisted in Milvus.
 */
public record MilvusVectorRecord(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        String content,
        float[] vector
) {
}
