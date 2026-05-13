package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.service.dto.AiApiChatResult;

import java.util.concurrent.CompletableFuture;

/**
 * Core RAG workflow service.
 */
public interface RagService {

    RagIngestResponse ingestFile(FileUploadResponse file,
                                 String kbCode,
                                 Long uploaderUserId,
                                 Integer chunkSize,
                                 Double overlapRatio);

    RagIngestResponse submitIngestFileAsync(FileUploadResponse file,
                                            String kbCode,
                                            Long uploaderUserId,
                                            Integer chunkSize,
                                            Double overlapRatio);

    CompletableFuture<RagIngestResponse> ingestFileAsync(FileUploadResponse file,
                                                         String kbCode,
                                                         Long uploaderUserId,
                                                         Integer chunkSize,
                                                         Double overlapRatio);

    RagRetrieveResponse retrieve(String queryText, Integer topK, Float minScore);

    AiApiChatResult streamAnswer(RagRetrieveResponse retrieval,
                                 String provider,
                                 String model,
                                 Double temperature,
                                 java.util.function.Consumer<String> contentConsumer);
}
