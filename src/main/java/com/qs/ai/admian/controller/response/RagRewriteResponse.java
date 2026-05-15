package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Query rewrite result.
 */
public record RagRewriteResponse(
        String originalQuery,
        String rewrittenQuery,
        Boolean changed,
        Boolean llmUsed,
        List<String> queryVariants
) {
}
