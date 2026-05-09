package com.qs.ai.admian.controller.response;

/**
 * Milvus collection status response.
 */
public record VectorCollectionResponse(
        String collectionName,
        Integer dimension,
        Boolean exists
) {
}
