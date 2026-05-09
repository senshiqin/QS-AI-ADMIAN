package com.qs.ai.admian.controller.request;

import java.util.List;

/**
 * Request for vector search. Use either queryText or vector.
 */
public record VectorSearchRequest(
        String queryText,
        List<Float> vector,
        Integer topK,
        Float minScore
) {
}
