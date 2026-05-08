package com.qs.ai.admian.service.impl;

import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.AiApiService;
import com.qs.ai.admian.service.dto.AiApiCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Normalizes AI provider exceptions into business exceptions handled globally.
 */
@Slf4j
@Service
public class AiApiServiceImpl implements AiApiService {

    private static final String TIMEOUT_MESSAGE = "\u0041\u0049\u670d\u52a1\u54cd\u5e94\u8d85\u65f6\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
    private static final String HTTP_STATUS_MESSAGE = "\u0041\u0049\u670d\u52a1\u8fd4\u56de\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
    private static final String NETWORK_MESSAGE = "\u0041\u0049\u670d\u52a1\u8fde\u63a5\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u6216\u7a0d\u540e\u91cd\u8bd5";
    private static final String DEFAULT_MESSAGE = "\u0041\u0049\u670d\u52a1\u8c03\u7528\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";

    @Override
    public void validateApiKey(String apiKey, String providerName) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AiApiException(providerName + " API Key\u672a\u914d\u7f6e\uff0c\u8bf7\u5148\u914d\u7f6e\u73af\u5883\u53d8\u91cf");
        }
    }

    @Override
    @Retryable(
            retryFor = {
                    TimeoutException.class,
                    HttpTimeoutException.class,
                    SocketTimeoutException.class,
                    ConnectException.class,
                    ResourceAccessException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000L)
    )
    public <T> T executeWithRetry(String operation, AiApiCall<T> apiCall) throws Exception {
        log.debug("Executing AI API call, operation={}", operation);
        return apiCall.execute();
    }

    @Override
    public AiApiException toAiApiException(String operation, Exception ex) {
        if (ex instanceof AiApiException aiApiException) {
            return aiApiException;
        }

        if (isTimeoutException(ex)) {
            log.warn("{} timeout: {}", operation, ex.getMessage(), ex);
            return new AiApiException(TIMEOUT_MESSAGE);
        }

        if (ex instanceof HttpStatusCodeException statusException) {
            log.warn("{} failed with HTTP status={}, response={}",
                    operation,
                    statusException.getStatusCode(),
                    statusException.getResponseBodyAsString(),
                    statusException);
            return new AiApiException(HTTP_STATUS_MESSAGE);
        }

        if (ex instanceof RestClientResponseException responseException) {
            log.warn("{} failed with HTTP status={}, response={}",
                    operation,
                    responseException.getStatusCode(),
                    responseException.getResponseBodyAsString(),
                    responseException);
            return new AiApiException(HTTP_STATUS_MESSAGE);
        }

        if (isConnectionException(ex)) {
            log.warn("{} network error: {}", operation, ex.getMessage(), ex);
            return new AiApiException(NETWORK_MESSAGE);
        }

        log.error("{} failed", operation, ex);
        return new AiApiException(DEFAULT_MESSAGE);
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isConnectionException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ResourceAccessException
                    || current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
