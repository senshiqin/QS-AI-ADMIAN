package com.qs.ai.admian.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Chat send request.
 */
@Data
public class ChatSendRequest {

    @Schema(description = "Conversation id, optional for first message", example = "conv-001")
    private String conversationId;

    @Schema(description = "User message content", example = "你好，介绍一下RAG")
    @NotBlank(message = "content must not be blank")
    private String content;

    @Schema(description = "Model type", example = "deepseek")
    @NotBlank(message = "modelType must not be blank")
    @Pattern(regexp = "^(deepseek|tongyi)$", message = "modelType must be deepseek or tongyi")
    private String modelType;
}
