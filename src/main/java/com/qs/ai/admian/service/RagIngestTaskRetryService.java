package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.response.RagIngestResponse;

/**
 * Retry orchestration for failed RAG ingest tasks.
 */
public interface RagIngestTaskRetryService {

    RagIngestResponse retry(Long taskId);
}
