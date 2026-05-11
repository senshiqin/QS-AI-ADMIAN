package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;

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
}
