package com.qs.ai.admian.controller.response;

import java.time.LocalDateTime;

/**
 * RAG ingest task status response.
 */
public record RagIngestTaskResponse(
        Long id,
        String taskNo,
        Long knowledgeFileId,
        String kbCode,
        String fileName,
        String storagePath,
        String status,
        Integer progressPercent,
        String currentStep,
        Integer retryCount,
        Integer maxRetry,
        Integer chunkSize,
        Double overlapRatio,
        Integer textLength,
        Integer chunkCount,
        Long storedVectorCount,
        String embeddingModel,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
