package com.qs.ai.admian.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.entity.AiRagIngestTask;

/**
 * Service for RAG ingest task status tracking.
 */
public interface AiRagIngestTaskService extends IService<AiRagIngestTask> {

    String STATUS_PENDING = "PENDING";
    String STATUS_RUNNING = "RUNNING";
    String STATUS_SUCCESS = "SUCCESS";
    String STATUS_FAILED = "FAILED";

    AiRagIngestTask createPendingTask(AiKnowledgeFile knowledgeFile, int chunkSize, double overlapRatio);

    void markRunning(Long taskId, String step);

    void updateProgress(Long taskId, int progressPercent, String step);

    void markSuccess(Long taskId,
                     int textLength,
                     int chunkCount,
                     long storedVectorCount,
                     String embeddingModel,
                     long durationMs);

    void markFailed(Long taskId, String errorMessage, long durationMs);

    AiRagIngestTask prepareRetry(Long taskId);
}
