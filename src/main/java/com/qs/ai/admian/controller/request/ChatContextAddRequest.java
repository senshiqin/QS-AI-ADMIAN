package com.qs.ai.admian.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for adding one chat context message.
 */
@Data
public class ChatContextAddRequest {

    @Schema(description = "User id", example = "1")
    @NotNull(message = "userId must not be null")
    private Long userId;

    @Schema(description = "Conversation id", example = "conv-001")
    @NotBlank(message = "conversationId must not be blank")
    private String conversationId;

    @Schema(description = "Role", example = "user")
    @NotBlank(message = "role must not be blank")
    private String role;

    @Schema(description = "Message content", example = "What is RAG?")
    @NotBlank(message = "content must not be blank")
    private String content;
}
