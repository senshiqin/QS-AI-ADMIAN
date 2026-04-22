package com.qs.ai.admian.util.response;

import lombok.Builder;

import java.util.List;

/**
 * Knowledge-base retrieval response data.
 */
@Builder
public record AiKnowledgeData(
        String question,
        List<KnowledgeItem> items,
        String summary
) {

    @Builder
    public record KnowledgeItem(
            String docId,
            String title,
            String snippet,
            Double score
    ) {
    }
}
