package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.controller.response.RagRetrievedChunkResponse;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiKnowledgeFileService;
import com.qs.ai.admian.service.RagService;
import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.service.dto.TextChunk;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.MilvusVectorUtil;
import com.qs.ai.admian.util.TextChunkUtil;
import com.qs.ai.admian.util.TextParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default implementation for RAG ingestion and retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String DEFAULT_KB_CODE = "default";
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final double DEFAULT_OVERLAP_RATIO = 0.10D;
    private static final int DEFAULT_TOP_K = 5;

    private final AiKnowledgeFileService aiKnowledgeFileService;
    private final AiEmbeddingUtil aiEmbeddingUtil;
    private final MilvusVectorUtil milvusVectorUtil;

    @Override
    public RagIngestResponse ingestFile(FileUploadResponse file,
                                        String kbCode,
                                        Long uploaderUserId,
                                        Integer chunkSize,
                                        Double overlapRatio) {
        if (file == null) {
            throw new ParamException("uploaded file metadata must not be null");
        }
        String safeKbCode = StringUtils.hasText(kbCode) ? kbCode : DEFAULT_KB_CODE;
        Long safeUploaderUserId = uploaderUserId == null ? 0L : uploaderUserId;
        int safeChunkSize = chunkSize == null || chunkSize <= 0 ? DEFAULT_CHUNK_SIZE : chunkSize;
        double safeOverlapRatio = overlapRatio == null || overlapRatio < 0 ? DEFAULT_OVERLAP_RATIO : overlapRatio;

        AiKnowledgeFile knowledgeFile = saveParsingFile(file, safeKbCode, safeUploaderUserId);
        try {
            String parsedText = TextParseUtil.parse(file.storagePath());
            List<TextChunk> chunks = TextChunkUtil.splitBySemantic(
                    knowledgeFile.getId(),
                    parsedText,
                    safeChunkSize,
                    safeOverlapRatio
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

            return new RagIngestResponse(
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
        } catch (RuntimeException ex) {
            knowledgeFile.setParseStatus(3);
            knowledgeFile.setRemark(ex.getMessage());
            knowledgeFile.setUpdateTime(LocalDateTime.now());
            aiKnowledgeFileService.updateById(knowledgeFile);
            throw ex;
        }
    }

    @Override
    public RagRetrieveResponse retrieve(String queryText, Integer topK, Float minScore) {
        String cleanQuery = TextParseUtil.cleanText(queryText);
        if (!StringUtils.hasText(cleanQuery)) {
            throw new ParamException("queryText must not be blank");
        }

        int safeTopK = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
        float[] queryVector = aiEmbeddingUtil.embed(cleanQuery);
        List<MilvusSearchResult> searchResults = milvusVectorUtil.similaritySearch(queryVector, safeTopK, minScore == null ? 0 : minScore);
        Map<Long, AiKnowledgeFile> fileMap = loadFileMap(searchResults);
        List<RagRetrievedChunkResponse> chunks = searchResults.stream()
                .map(result -> toChunkResponse(result, fileMap.get(result.fileId())))
                .toList();

        return new RagRetrieveResponse(
                cleanQuery,
                safeTopK,
                minScore == null || minScore <= 0 ? milvusVectorUtil.getSimilarityThreshold() : minScore,
                aiEmbeddingUtil.getEmbeddingModel(),
                milvusVectorUtil.getEmbeddingDimension(),
                chunks.size(),
                chunks,
                buildRagContext(chunks)
        );
    }

    private AiKnowledgeFile saveParsingFile(FileUploadResponse file, String kbCode, Long uploaderUserId) {
        String fileHash = calculateSha256(file.storagePath());
        AiKnowledgeFile knowledgeFile = aiKnowledgeFileService.getOne(new LambdaQueryWrapper<AiKnowledgeFile>()
                .eq(AiKnowledgeFile::getKbCode, kbCode)
                .eq(AiKnowledgeFile::getFileHash, fileHash)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (knowledgeFile == null) {
            knowledgeFile = new AiKnowledgeFile();
            knowledgeFile.setCreateTime(now);
        }

        knowledgeFile.setKbCode(kbCode);
        knowledgeFile.setFileName(file.originalFilename());
        knowledgeFile.setFileType(file.fileExtension());
        knowledgeFile.setFileSize(file.fileSize());
        knowledgeFile.setStoragePath(file.storagePath());
        knowledgeFile.setFileHash(fileHash);
        knowledgeFile.setParseStatus(1);
        knowledgeFile.setChunkCount(0);
        knowledgeFile.setUploaderUserId(uploaderUserId);
        knowledgeFile.setDeleted(0);
        knowledgeFile.setRemark(null);
        knowledgeFile.setUpdateTime(now);
        aiKnowledgeFileService.saveOrUpdate(knowledgeFile);
        return knowledgeFile;
    }

    private Map<Long, AiKnowledgeFile> loadFileMap(List<MilvusSearchResult> searchResults) {
        List<Long> fileIds = searchResults.stream()
                .map(MilvusSearchResult::fileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return aiKnowledgeFileService.listByIds(fileIds).stream()
                .collect(Collectors.toMap(AiKnowledgeFile::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private RagRetrievedChunkResponse toChunkResponse(MilvusSearchResult result, AiKnowledgeFile file) {
        return new RagRetrievedChunkResponse(
                result.chunkId(),
                result.fileId(),
                result.chunkIndex(),
                result.score(),
                result.content(),
                file == null ? null : file.getFileName(),
                file == null ? null : file.getFileType(),
                file == null ? null : file.getStoragePath(),
                file == null ? null : file.getKbCode()
        );
    }

    private String buildRagContext(List<RagRetrievedChunkResponse> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagRetrievedChunkResponse chunk = chunks.get(i);
            context.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append(nullToEmpty(chunk.fileName()))
                    .append(" #")
                    .append(chunk.chunkIndex())
                    .append(System.lineSeparator())
                    .append(chunk.content())
                    .append(System.lineSeparator());
        }
        return context.toString();
    }

    private String calculateSha256(String storagePath) {
        try (InputStream inputStream = Files.newInputStream(Path.of(storagePath))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            log.error("Failed to calculate file hash, storagePath={}", storagePath, ex);
            throw new ParamException("failed to calculate file hash");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
