package com.qs.ai.admian.controller.response;

/**
 * RAG ingestion result.
 */
public record RagIngestResponse(
        Long taskId,
        Long fileId,
        String kbCode,
        String fileName,
        String fileType,
        String storagePath,
        Integer textLength,
        Integer chunkCount,
        String embeddingModel,
        Integer embeddingDimension,
        Long storedVectorCount,
        Integer parseStatus
) {
}
