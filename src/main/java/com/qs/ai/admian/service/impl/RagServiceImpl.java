package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.RagProperties;
import com.qs.ai.admian.metrics.AiMetricsRecorder;
import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.controller.response.RagRetrievedChunkResponse;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiKnowledgeFileService;
import com.qs.ai.admian.service.AiRagIngestTaskService;
import com.qs.ai.admian.service.RagIngestAsyncTask;
import com.qs.ai.admian.service.RagService;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.service.dto.TextChunk;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.MilvusVectorUtil;
import com.qs.ai.admian.util.MultiModelChatUtil;
import com.qs.ai.admian.util.RedisUtil;
import com.qs.ai.admian.util.TextChunkUtil;
import com.qs.ai.admian.util.TextParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default implementation for RAG ingestion and retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String DEFAULT_KB_CODE = "default";
    private static final String RAG_RETRIEVE_CACHE_PREFIX = "ai:rag:retrieve:";
    private static final long RAG_RETRIEVE_CACHE_TTL_MINUTES = 10L;
    private final AiKnowledgeFileService aiKnowledgeFileService;
    private final AiEmbeddingUtil aiEmbeddingUtil;
    private final MilvusVectorUtil milvusVectorUtil;
    private final MultiModelChatUtil multiModelChatUtil;
    private final RagProperties ragProperties;
    private final RagIngestAsyncTask ragIngestAsyncTask;
    private final AiRagIngestTaskService aiRagIngestTaskService;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final AiMetricsRecorder aiMetricsRecorder;

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
        int safeChunkSize = chunkSize == null || chunkSize <= 0 ? resolveDefaultChunkSize() : chunkSize;
        double safeOverlapRatio = overlapRatio == null || overlapRatio < 0
                ? resolveDefaultOverlapRatio()
                : overlapRatio;

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
                    null,
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
    public RagIngestResponse submitIngestFileAsync(FileUploadResponse file,
                                                   String kbCode,
                                                   Long uploaderUserId,
                                                   Integer chunkSize,
                                                   Double overlapRatio) {
        if (file == null) {
            throw new ParamException("uploaded file metadata must not be null");
        }
        String safeKbCode = StringUtils.hasText(kbCode) ? kbCode : DEFAULT_KB_CODE;
        Long safeUploaderUserId = uploaderUserId == null ? 0L : uploaderUserId;
        int safeChunkSize = chunkSize == null || chunkSize <= 0 ? resolveDefaultChunkSize() : chunkSize;
        double safeOverlapRatio = overlapRatio == null || overlapRatio < 0
                ? resolveDefaultOverlapRatio()
                : overlapRatio;

        AiKnowledgeFile knowledgeFile = saveParsingFile(file, safeKbCode, safeUploaderUserId);
        Long taskId = aiRagIngestTaskService
                .createPendingTask(knowledgeFile, safeChunkSize, safeOverlapRatio)
                .getId();
        ragIngestAsyncTask.ingest(file, knowledgeFile.getId(), taskId, safeChunkSize, safeOverlapRatio)
                .exceptionally(ex -> {
                    log.error("RAG async ingest future completed exceptionally, fileId={}",
                            knowledgeFile.getId(), ex);
                    return null;
                });
        return new RagIngestResponse(
                taskId,
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

    @Async("aiTaskExecutor")
    @Override
    public CompletableFuture<RagIngestResponse> ingestFileAsync(FileUploadResponse file,
                                                                String kbCode,
                                                                Long uploaderUserId,
                                                                Integer chunkSize,
                                                                Double overlapRatio) {
        try {
            return CompletableFuture.completedFuture(ingestFile(file, kbCode, uploaderUserId, chunkSize, overlapRatio));
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Override
    public RagRetrieveResponse retrieve(String queryText, Integer topK, Float minScore) {
        long startTime = System.currentTimeMillis();
        String cleanQuery = TextParseUtil.cleanText(queryText);
        if (!StringUtils.hasText(cleanQuery)) {
            throw new ParamException("queryText must not be blank");
        }

        int safeTopK = topK == null || topK <= 0 ? resolveDefaultTopK() : topK;
        float safeMinScore = minScore == null || minScore <= 0 ? resolveDefaultMinScore() : minScore;
        String cacheKey = buildRetrieveCacheKey(cleanQuery, safeTopK, safeMinScore);
        String queryHash = sha256(cleanQuery);
        long redisGetStartTime = System.currentTimeMillis();
        RagRetrieveResponse cached = getCachedRetrieve(cacheKey);
        long redisGetMs = System.currentTimeMillis() - redisGetStartTime;
        if (cached != null) {
            log.info("RAG retrieve timing, cacheHit=true, queryHash={}, topK={}, minScore={}, "
                            + "redisGetMs={}, totalMs={}, hitCount={}",
                    queryHash, safeTopK, safeMinScore, redisGetMs, System.currentTimeMillis() - startTime,
                    cached.hitCount());
            aiMetricsRecorder.recordRagRetrieve(true, true, cached.hitCount(),
                    System.currentTimeMillis() - startTime);
            return cached;
        }

        try {
            int candidateTopK = safeTopK * resolveCandidateMultiplier();
            long embeddingStartTime = System.currentTimeMillis();
            float[] queryVector = aiEmbeddingUtil.embed(cleanQuery);
            long embeddingMs = System.currentTimeMillis() - embeddingStartTime;

            long milvusStartTime = System.currentTimeMillis();
            List<MilvusSearchResult> rawSearchResults = milvusVectorUtil.similaritySearch(queryVector,
                    candidateTopK, safeMinScore);
            long milvusMs = System.currentTimeMillis() - milvusStartTime;

            long rerankStartTime = System.currentTimeMillis();
            List<MilvusSearchResult> searchResults = refineSearchResults(rawSearchResults, safeTopK);
            long rerankMs = System.currentTimeMillis() - rerankStartTime;

            long dbStartTime = System.currentTimeMillis();
            Map<Long, AiKnowledgeFile> fileMap = loadFileMap(searchResults);
            long dbMs = System.currentTimeMillis() - dbStartTime;

            long buildStartTime = System.currentTimeMillis();
            List<RagRetrievedChunkResponse> chunks = searchResults.stream()
                    .map(result -> toChunkResponse(result, fileMap.get(result.fileId())))
                    .toList();

            RagRetrieveResponse response = new RagRetrieveResponse(
                    cleanQuery,
                    safeTopK,
                    safeMinScore,
                    aiEmbeddingUtil.getEmbeddingModel(),
                    milvusVectorUtil.getEmbeddingDimension(),
                    chunks.size(),
                    chunks,
                    buildRagContext(chunks)
            );
            long buildMs = System.currentTimeMillis() - buildStartTime;

            long redisSetStartTime = System.currentTimeMillis();
            cacheRetrieve(cacheKey, response);
            long redisSetMs = System.currentTimeMillis() - redisSetStartTime;
            log.info("RAG retrieve timing, cacheHit=false, queryHash={}, topK={}, candidateTopK={}, minScore={}, "
                            + "redisGetMs={}, embeddingMs={}, milvusMs={}, rerankMs={}, dbMs={}, buildMs={}, "
                            + "redisSetMs={}, totalMs={}, hitCount={}",
                    queryHash, safeTopK, candidateTopK, safeMinScore, redisGetMs, embeddingMs, milvusMs, rerankMs,
                    dbMs, buildMs, redisSetMs, System.currentTimeMillis() - startTime, response.hitCount());
            aiMetricsRecorder.recordRagRetrieve(true, false, response.hitCount(),
                    System.currentTimeMillis() - startTime);
            return response;
        } catch (RuntimeException ex) {
            aiMetricsRecorder.recordRagRetrieve(false, false, 0,
                    System.currentTimeMillis() - startTime);
            log.warn("RAG retrieve failed, queryHash={}, totalMs={}", queryHash,
                    System.currentTimeMillis() - startTime, ex);
            throw ex;
        }
    }

    @Override
    public AiApiChatResult streamAnswer(RagRetrieveResponse retrieval,
                                        String provider,
                                        String model,
                                        Double temperature,
                                        java.util.function.Consumer<String> contentConsumer) {
        if (retrieval == null) {
            throw new ParamException("retrieval result must not be null");
        }
        String safeModel = StringUtils.hasText(model) ? model : multiModelChatUtil.defaultModel(provider);
        Double safeTemperature = temperature == null ? resolveAnswerTemperature() : temperature;
        List<AiChatMessage> messages = List.of(
                AiChatMessage.builder()
                        .role("system")
                        .content(buildSystemPrompt())
                        .build(),
                AiChatMessage.builder()
                        .role("user")
                        .content(buildUserPrompt(retrieval))
                        .build()
        );

        return multiModelChatUtil.streamChat(
                provider,
                messages,
                AiChatOptions.builder()
                        .model(safeModel)
                        .temperature(safeTemperature)
                        .maxTokens(resolveAnswerMaxTokens())
                        .maxInputTokens(resolveMaxInputTokens())
                        .build(),
                contentConsumer::accept
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

    private List<MilvusSearchResult> refineSearchResults(List<MilvusSearchResult> results, int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        Set<String> seenContent = new LinkedHashSet<>();
        List<MilvusSearchResult> refined = new ArrayList<>();
        results.stream()
                .filter(result -> StringUtils.hasText(result.content()))
                .filter(result -> result.content().length() >= resolveMinChunkChars())
                .sorted(Comparator.comparing(
                        MilvusSearchResult::score,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .forEach(result -> {
                    String fingerprint = contentFingerprint(result.content());
                    if (seenContent.add(fingerprint)) {
                        refined.add(result);
                    }
                });
        return refined.stream()
                .limit(topK)
                .toList();
    }

    private String buildRagContext(List<RagRetrievedChunkResponse> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagRetrievedChunkResponse chunk = chunks.get(i);
            String chunkBlock = "["
                    + (i + 1)
                    + "] 来源: "
                    + nullToEmpty(chunk.fileName())
                    + " | 文件ID: "
                    + chunk.fileId()
                    + " | 切片: "
                    + chunk.chunkIndex()
                    + " | 相似度: "
                    + formatScore(chunk.score())
                    + System.lineSeparator()
                    + chunk.content()
                    + System.lineSeparator();
            if (context.length() + chunkBlock.length() > resolveMaxContextChars()) {
                break;
            }
            context.append(chunkBlock);
        }
        return context.toString();
    }

    private String buildSystemPrompt() {
        return """
                你是一个严格基于知识库证据回答的 RAG 助手。

                必须遵守：
                1. 只能依据“参考资料”中的内容回答，不要使用没有证据支持的外部知识。
                2. 如果参考资料不足以回答，直接说明“当前知识库没有足够依据回答该问题”，不要猜测。
                3. 回答中涉及事实、结论、步骤时，尽量标注引用编号，例如 [1]、[2]。
                4. 如果多个切片说法冲突，指出冲突并说明无法确认。
                5. 回答要简洁、结构清晰，优先给出与用户问题直接相关的内容。
                """;
    }

    private String buildUserPrompt(RagRetrieveResponse retrieval) {
        String context = StringUtils.hasText(retrieval.ragContext())
                ? retrieval.ragContext()
                : "未检索到满足阈值的参考资料。";
        return """
                用户问题：
                %s

                参考资料：
                %s

                请基于上述参考资料用中文回答。若资料不足，请明确说明不能回答，并给出缺少什么信息。
                """.formatted(retrieval.queryText(), context);
    }

    private String contentFingerprint(String content) {
        String clean = TextParseUtil.cleanText(content);
        int end = Math.min(clean.length(), 160);
        return clean.substring(0, end);
    }

    private String formatScore(Float score) {
        if (score == null) {
            return "N/A";
        }
        return String.format(java.util.Locale.ROOT, "%.4f", score);
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

    private String buildRetrieveCacheKey(String cleanQuery, int topK, float minScore) {
        String source = aiEmbeddingUtil.getEmbeddingModel()
                + ":"
                + milvusVectorUtil.getEmbeddingDimension()
                + ":"
                + topK
                + ":"
                + minScore
                + ":"
                + cleanQuery;
        return RAG_RETRIEVE_CACHE_PREFIX + sha256(source);
    }

    private RagRetrieveResponse getCachedRetrieve(String cacheKey) {
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached == null) {
                return null;
            }
            if (cached instanceof String json) {
                return objectMapper.readValue(json, RagRetrieveResponse.class);
            }
            return objectMapper.convertValue(cached, RagRetrieveResponse.class);
        } catch (Exception ex) {
            log.debug("Failed to read RAG retrieve cache, key={}", cacheKey, ex);
            return null;
        }
    }

    private void cacheRetrieve(String cacheKey, RagRetrieveResponse response) {
        try {
            redisUtil.set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    RAG_RETRIEVE_CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception ex) {
            log.debug("Failed to write RAG retrieve cache, key={}", cacheKey, ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to calculate cache key", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int resolveDefaultChunkSize() {
        Integer value = ragProperties.getDefaultChunkSize();
        return value == null || value <= 0 ? 800 : value;
    }

    private double resolveDefaultOverlapRatio() {
        Double value = ragProperties.getDefaultOverlapRatio();
        return value == null || value < 0 ? 0.15D : value;
    }

    private int resolveDefaultTopK() {
        Integer value = ragProperties.getDefaultTopK();
        return value == null || value <= 0 ? 5 : value;
    }

    private float resolveDefaultMinScore() {
        Float value = ragProperties.getDefaultMinScore();
        return value == null || value <= 0 ? milvusVectorUtil.getSimilarityThreshold() : value;
    }

    private int resolveCandidateMultiplier() {
        Integer value = ragProperties.getCandidateMultiplier();
        return value == null || value <= 0 ? 3 : value;
    }

    private int resolveMaxContextChars() {
        Integer value = ragProperties.getMaxContextChars();
        return value == null || value <= 0 ? 6000 : value;
    }

    private int resolveMinChunkChars() {
        Integer value = ragProperties.getMinChunkChars();
        return value == null || value <= 0 ? 20 : value;
    }

    private int resolveAnswerMaxTokens() {
        Integer value = ragProperties.getAnswerMaxTokens();
        return value == null || value <= 0 ? 1200 : value;
    }

    private int resolveMaxInputTokens() {
        Integer value = ragProperties.getMaxInputTokens();
        return value == null || value <= 0 ? 6000 : value;
    }

    private double resolveAnswerTemperature() {
        Double value = ragProperties.getAnswerTemperature();
        return value == null || value < 0 ? 0.2D : value;
    }
}
