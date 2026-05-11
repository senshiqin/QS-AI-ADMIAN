package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.service.dto.AiApiChatResult;

/**
 * Core RAG workflow service.
 */
public interface RagService {

    RagIngestResponse ingestFile(FileUploadResponse file,
                                 String kbCode,
                                 Long uploaderUserId,
                                 Integer chunkSize,
                                 Double overlapRatio);

    RagRetrieveResponse retrieve(String queryText, Integer topK, Float minScore);

    AiApiChatResult streamAnswer(RagRetrieveResponse retrieval,
                                 String model,
                                 Double temperature,
                                 java.util.function.Consumer<String> contentConsumer);
}
