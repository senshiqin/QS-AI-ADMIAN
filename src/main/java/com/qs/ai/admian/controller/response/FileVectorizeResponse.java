package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Response for upload, parse, chunk and embedding workflow.
 */
public record FileVectorizeResponse(
        String originalFilename,
        String storedFilename,
        String storagePath,
        Long fileId,
        Integer textLength,
        Integer chunkCount,
        String embeddingModel,
        Integer embeddingDimension,
        Boolean storedToMilvus,
        Long storedVectorCount,
        List<FileVectorizeChunkResponse> chunks
) {
}
