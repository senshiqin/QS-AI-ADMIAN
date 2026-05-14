package com.qs.ai.admian.service.impl;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.entity.AiRagIngestTask;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiKnowledgeFileService;
import com.qs.ai.admian.service.AiRagIngestTaskService;
import com.qs.ai.admian.service.RagIngestAsyncTask;
import com.qs.ai.admian.service.RagIngestTaskRetryService;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.MilvusVectorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default retry orchestration for failed RAG ingest tasks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestTaskRetryServiceImpl implements RagIngestTaskRetryService {

    private final AiRagIngestTaskService aiRagIngestTaskService;
    private final AiKnowledgeFileService aiKnowledgeFileService;
    private final RagIngestAsyncTask ragIngestAsyncTask;
    private final AiEmbeddingUtil aiEmbeddingUtil;
    private final MilvusVectorUtil milvusVectorUtil;

    @Override
    public RagIngestResponse retry(Long taskId) {
        AiRagIngestTask task = aiRagIngestTaskService.prepareRetry(taskId);
        AiKnowledgeFile knowledgeFile = aiKnowledgeFileService.getById(task.getKnowledgeFileId());
        if (knowledgeFile == null || Integer.valueOf(1).equals(knowledgeFile.getDeleted())) {
            aiRagIngestTaskService.markFailed(task.getId(), "knowledge file not found or deleted", 0);
            throw new ParamException("knowledge file not found or deleted: " + task.getKnowledgeFileId());
        }

        knowledgeFile.setParseStatus(1);
        knowledgeFile.setRemark(null);
        knowledgeFile.setChunkCount(0);
        knowledgeFile.setUpdateTime(java.time.LocalDateTime.now());
        aiKnowledgeFileService.updateById(knowledgeFile);

        FileUploadResponse file = new FileUploadResponse(
                knowledgeFile.getFileName(),
                knowledgeFile.getFileName(),
                knowledgeFile.getFileType(),
                knowledgeFile.getFileSize(),
                knowledgeFile.getStoragePath()
        );
        ragIngestAsyncTask.ingest(
                file,
                knowledgeFile.getId(),
                task.getId(),
                task.getChunkSize() == null || task.getChunkSize() <= 0 ? 800 : task.getChunkSize(),
                task.getOverlapRatio() == null || task.getOverlapRatio() < 0 ? 0.15D : task.getOverlapRatio()
        ).exceptionally(ex -> {
            log.error("RAG ingest retry future completed exceptionally, taskId={}, fileId={}",
                    task.getId(), knowledgeFile.getId(), ex);
            return null;
        });

        return new RagIngestResponse(
                task.getId(),
                knowledgeFile.getId(),
                knowledgeFile.getKbCode(),
                knowledgeFile.getFileName(),
                knowledgeFile.getFileType(),
                knowledgeFile.getStoragePath(),
                0,
                0,
                aiEmbeddingUtil.getEmbeddingModel(),
                milvusVectorUtil.getEmbeddingDimension(),
                0L,
                knowledgeFile.getParseStatus()
        );
    }
}
