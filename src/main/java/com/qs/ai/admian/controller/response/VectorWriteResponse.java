package com.qs.ai.admian.controller.response;

/**
 * Milvus vector write response.
 */
public record VectorWriteResponse(
        Long affectedCount
) {
}
