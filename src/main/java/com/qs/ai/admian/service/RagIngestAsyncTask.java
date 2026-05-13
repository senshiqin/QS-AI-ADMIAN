package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.dto.TextChunk;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.MilvusVectorUtil;
import com.qs.ai.admian.util.TextChunkUtil;
import com.qs.ai.admian.util.TextParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * RAG 文件入库异步任务，负责解析、向量化和 Milvus 写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestAsyncTask {

    private final AiKnowledgeFileService aiKnowledgeFileService;
    private final AiEmbeddingUtil aiEmbeddingUtil;
    private final MilvusVectorUtil milvusVectorUtil;

    @Async("aiTaskExecutor")
    public CompletableFuture<RagIngestResponse> ingest(FileUploadResponse file,
                                                       Long knowledgeFileId,
                                                       int chunkSize,
                                                       double overlapRatio) {
        AiKnowledgeFile knowledgeFile = aiKnowledgeFileService.getById(knowledgeFileId);
        if (knowledgeFile == null) {
            return CompletableFuture.failedFuture(new ParamException("knowledge file not found: " + knowledgeFileId));
        }

        try {
            log.info("RAG async ingest started, fileId={}, fileName={}",
                    knowledgeFileId, knowledgeFile.getFileName());
            String parsedText = TextParseUtil.parse(file.storagePath());
            List<TextChunk> chunks = TextChunkUtil.splitBySemantic(
                    knowledgeFile.getId(),
                    parsedText,
                    chunkSize,
                    overlapRatio
            );
            if (chunks.isEmpty()) {
                throw new ParamException("parsed text does not contain valid chunks");
            }

            List<float[]> vectors = aiEmbeddingUtil.embedBatch(chunks.stream()
                    .map(TextChunk::content)
                    .toList());
            milvusVectorUtil.deleteByFileId(knowledgeFile.getId());
            long storedVectorCount = milvusVectorUtil.batchInsert(chunks, vectors);

            knowledgeFile.setParseStatus(2);
            knowledgeFile.setChunkCount(chunks.size());
            knowledgeFile.setEmbeddingModel(aiEmbeddingUtil.getEmbeddingModel());
            knowledgeFile.setVectorIndexName("milvus:" + knowledgeFile.getId());
            knowledgeFile.setLastParseTime(LocalDateTime.now());
            knowledgeFile.setUpdateTime(LocalDateTime.now());
            aiKnowledgeFileService.updateById(knowledgeFile);

            RagIngestResponse response = new RagIngestResponse(
                    knowledgeFile.getId(),
                    knowledgeFile.getKbCode(),
                    knowledgeFile.getFileName(),
                    knowledgeFile.getFileType(),
                    knowledgeFile.getStoragePath(),
                    parsedText.length(),
                    chunks.size(),
                    aiEmbeddingUtil.getEmbeddingModel(),
                    milvusVectorUtil.getEmbeddingDimension(),
                    storedVectorCount,
                    knowledgeFile.getParseStatus()
            );
            log.info("RAG async ingest completed, fileId={}, chunkCount={}, storedVectorCount={}",
                    knowledgeFileId, chunks.size(), storedVectorCount);
            return CompletableFuture.completedFuture(response);
        } catch (Exception ex) {
            knowledgeFile.setParseStatus(3);
            knowledgeFile.setRemark(ex.getMessage());
            knowledgeFile.setUpdateTime(LocalDateTime.now());
            aiKnowledgeFileService.updateById(knowledgeFile);
            log.error("RAG async ingest failed, fileId={}", knowledgeFileId, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}
