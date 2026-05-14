package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.entity.AiRagIngestTask;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.mapper.AiRagIngestTaskMapper;
import com.qs.ai.admian.service.AiRagIngestTaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default RAG ingest task status service.
 */
@Service
public class AiRagIngestTaskServiceImpl
        extends ServiceImpl<AiRagIngestTaskMapper, AiRagIngestTask>
        implements AiRagIngestTaskService {

    private static final int DEFAULT_MAX_RETRY = 3;

    @Override
    public AiRagIngestTask createPendingTask(AiKnowledgeFile knowledgeFile, int chunkSize, double overlapRatio) {
        if (knowledgeFile == null || knowledgeFile.getId() == null) {
            throw new ParamException("knowledge file must not be null");
        }
        LocalDateTime now = LocalDateTime.now();
        AiRagIngestTask task = new AiRagIngestTask();
        task.setTaskNo("RAG-" + UUID.randomUUID().toString().replace("-", ""));
        task.setKnowledgeFileId(knowledgeFile.getId());
        task.setKbCode(knowledgeFile.getKbCode());
        task.setFileName(knowledgeFile.getFileName());
        task.setStoragePath(knowledgeFile.getStoragePath());
        task.setStatus(STATUS_PENDING);
        task.setProgressPercent(0);
        task.setCurrentStep("submitted");
        task.setRetryCount(0);
        task.setMaxRetry(DEFAULT_MAX_RETRY);
        task.setChunkSize(chunkSize);
        task.setOverlapRatio(overlapRatio);
        task.setTextLength(0);
        task.setChunkCount(0);
        task.setStoredVectorCount(0L);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        save(task);
        return task;
    }

    @Override
    public void markRunning(Long taskId, String step) {
        AiRagIngestTask task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(STATUS_RUNNING);
        task.setProgressPercent(Math.max(task.getProgressPercent() == null ? 0 : task.getProgressPercent(), 5));
        task.setCurrentStep(safeStep(step));
        task.setStartedAt(now);
        task.setFinishedAt(null);
        task.setDurationMs(null);
        task.setErrorMessage(null);
        task.setUpdateTime(now);
        updateById(task);
    }

    @Override
    public void updateProgress(Long taskId, int progressPercent, String step) {
        AiRagIngestTask task = requireTask(taskId);
        task.setStatus(STATUS_RUNNING);
        task.setProgressPercent(clampProgress(progressPercent));
        task.setCurrentStep(safeStep(step));
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
    }

    @Override
    public void markSuccess(Long taskId,
                            int textLength,
                            int chunkCount,
                            long storedVectorCount,
                            String embeddingModel,
                            long durationMs) {
        AiRagIngestTask task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(STATUS_SUCCESS);
        task.setProgressPercent(100);
        task.setCurrentStep("completed");
        task.setTextLength(textLength);
        task.setChunkCount(chunkCount);
        task.setStoredVectorCount(storedVectorCount);
        task.setEmbeddingModel(embeddingModel);
        task.setErrorMessage(null);
        task.setFinishedAt(now);
        task.setDurationMs(Math.max(durationMs, 0));
        task.setUpdateTime(now);
        updateById(task);
    }

    @Override
    public void markFailed(Long taskId, String errorMessage, long durationMs) {
        AiRagIngestTask task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(STATUS_FAILED);
        task.setCurrentStep("failed");
        task.setErrorMessage(truncate(errorMessage));
        task.setFinishedAt(now);
        task.setDurationMs(Math.max(durationMs, 0));
        task.setUpdateTime(now);
        updateById(task);
    }

    @Override
    public AiRagIngestTask prepareRetry(Long taskId) {
        AiRagIngestTask task = requireTask(taskId);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw new ParamException("only failed task can be retried");
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? DEFAULT_MAX_RETRY : task.getMaxRetry();
        if (retryCount >= maxRetry) {
            throw new ParamException("task retry limit exceeded");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(STATUS_PENDING);
        task.setProgressPercent(0);
        task.setCurrentStep("retry submitted");
        task.setRetryCount(retryCount + 1);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setDurationMs(null);
        task.setUpdateTime(now);
        updateById(task);
        return task;
    }

    private AiRagIngestTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new ParamException("taskId must not be null");
        }
        AiRagIngestTask task = getById(taskId);
        if (task == null) {
            throw new ParamException("RAG ingest task not found: " + taskId);
        }
        return task;
    }

    private int clampProgress(int progressPercent) {
        return Math.max(0, Math.min(progressPercent, 99));
    }

    private String safeStep(String step) {
        return StringUtils.hasText(step) ? step : "running";
    }

    private String truncate(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
