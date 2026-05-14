package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Paginated RAG ingest task response.
 */
public record RagIngestTaskPageResponse(
        Long pageNo,
        Long pageSize,
        Long total,
        Long pages,
        List<RagIngestTaskResponse> records
) {
}
