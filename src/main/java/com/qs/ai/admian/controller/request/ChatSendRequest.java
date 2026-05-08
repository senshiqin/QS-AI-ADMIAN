package com.qs.ai.admian.controller.request;

import com.qs.ai.admian.service.dto.AiChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.util.List;

/**
 * Chat send request.
 */
@Data
public class ChatSendRequest {

    @Schema(description = "Conversation id, optional for first message", example = "conv-001")
    private String conversationId;

    @Schema(description = "LLM model name", example = "qwen-turbo", defaultValue = "qwen-turbo")
    private String model = "qwen-turbo";

    @Schema(description = "Chat messages")
    @Valid
    private List<AiChatMessage> messages;

    @Schema(description = "Sampling temperature", example = "0.7", defaultValue = "0.7")
    @DecimalMin(value = "0.0", message = "temperature must be greater than or equal to 0")
    @DecimalMax(value = "2.0", message = "temperature must be less than or equal to 2")
    private Double temperature = 0.7D;

    @Schema(description = "Legacy user message content", example = "hello")
    private String content;

    @Schema(description = "Legacy model type", example = "tongyi")
    private String modelType;
}
