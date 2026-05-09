package com.qs.ai.admian.controller.response;

/**
 * Uploaded file metadata.
 */
public record FileUploadResponse(
        String originalFilename,
        String storedFilename,
        String fileExtension,
        Long fileSize,
        String storagePath
) {
}
