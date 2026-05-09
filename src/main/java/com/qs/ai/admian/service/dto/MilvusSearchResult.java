package com.qs.ai.admian.service.dto;

/**
 * Milvus vector search result for RAG retrieval.
 */
public record MilvusSearchResult(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        String content,
        Float score
) {
}
