package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request for manual vector upsert.
 */
public record VectorUpsertRequest(
        @NotBlank(message = "chunkId must not be blank")
        String chunkId,

        @NotNull(message = "fileId must not be null")
        Long fileId,

        @NotNull(message = "chunkIndex must not be null")
        Integer chunkIndex,

        @NotBlank(message = "content must not be blank")
        String content,

        @NotEmpty(message = "vector must not be empty")
        List<Float> vector
) {
}
