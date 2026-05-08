package com.qs.ai.admian.service;

import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.dto.AiApiCall;
import com.qs.ai.admian.service.dto.AiChatMessage;

import java.util.List;

/**
 * Common AI API helper for provider calls.
 */
public interface AiApiService {

    void validateApiKey(String apiKey, String providerName);

    <T> T executeWithRetry(String operation, AiApiCall<T> apiCall) throws Exception;

    int estimateTokens(String text);

    int estimateMessagesTokens(List<AiChatMessage> messages);

    void validateInputTokens(List<AiChatMessage> messages, Integer maxInputTokens);

    AiApiException toAiApiException(String operation, Exception ex);
}
