package com.qs.ai.admian.controller.response;

/**
 * Retrieved chunk with rerank score details.
 */
public record RagRerankChunkResponse(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        Float vectorScore,
        Double keywordScore,
        Double rerankScore,
        String content,
        String fileName,
        String fileType,
        String storagePath,
        String kbCode
) {
}
