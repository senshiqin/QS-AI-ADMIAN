package com.qs.ai.admian.service;

import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.dto.AiApiCall;

/**
 * Common AI API helper for provider calls.
 */
public interface AiApiService {

    void validateApiKey(String apiKey, String providerName);

    <T> T executeWithRetry(String operation, AiApiCall<T> apiCall) throws Exception;

    AiApiException toAiApiException(String operation, Exception ex);
}
