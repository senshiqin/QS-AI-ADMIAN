package com.qs.ai.admian.controller.response;

/**
 * Source chunk returned by LangChain4j RAG chain.
 */
public record LangChain4jRagChunkResponse(
        String chunkId,
        Long fileId,
        Integer chunkIndex,
        Float score,
        String fileName,
        String fileType,
        String storagePath,
        String kbCode,
        String content
) {
}
