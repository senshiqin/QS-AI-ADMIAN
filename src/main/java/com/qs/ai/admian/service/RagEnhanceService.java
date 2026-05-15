package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.request.RagAdvancedRetrieveRequest;
import com.qs.ai.admian.controller.request.RagEvalRequest;
import com.qs.ai.admian.controller.request.RagRewriteRequest;
import com.qs.ai.admian.controller.response.RagAdvancedRetrieveResponse;
import com.qs.ai.admian.controller.response.RagEvalResponse;
import com.qs.ai.admian.controller.response.RagRewriteResponse;

/**
 * RAG enhancement service for query rewrite, rerank and evaluation.
 */
public interface RagEnhanceService {

    RagRewriteResponse rewrite(RagRewriteRequest request);

    RagAdvancedRetrieveResponse advancedRetrieve(RagAdvancedRetrieveRequest request);

    RagEvalResponse evaluate(RagEvalRequest request);
}
