package com.qs.ai.admian.controller.response;

/**
 * Preview metadata for one vectorized text chunk.
 */
public record FileVectorizeChunkResponse(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        Integer startOffset,
        Integer endOffset,
        Integer contentLength,
        String contentPreview,
        Integer vectorDimension
) {
}
