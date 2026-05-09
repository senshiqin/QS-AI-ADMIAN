package com.qs.ai.admian.service.dto;

/**
 * Text chunk metadata for RAG indexing and retrieval.
 */
public record TextChunk(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        String content,
        Integer startOffset,
        Integer endOffset
) {
}
