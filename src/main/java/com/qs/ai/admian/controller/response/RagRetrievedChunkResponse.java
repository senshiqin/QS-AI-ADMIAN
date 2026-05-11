package com.qs.ai.admian.controller.response;

/**
 * Retrieved chunk with source file metadata.
 */
public record RagRetrievedChunkResponse(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        Float score,
        String content,
        String fileName,
        String fileType,
        String storagePath,
        String kbCode
) {
}
