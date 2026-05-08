package com.qs.ai.admian.service.dto;

/**
 * External AI API call that may throw provider/network exceptions.
 */
@FunctionalInterface
public interface AiApiCall<T> {

    T execute() throws Exception;
}
