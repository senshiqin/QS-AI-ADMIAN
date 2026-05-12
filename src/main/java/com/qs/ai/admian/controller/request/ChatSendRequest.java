package com.qs.ai.admian.controller.request;

import com.qs.ai.admian.service.dto.AiChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * Chat send request.
 */
@Data
public class ChatSendRequest {

    @Schema(description = "Conversation id, optional for first message", example = "conv-001")
    private String conversationId;

    @Schema(description = "AI provider key or alias. Auto-detect by model prefix if empty.", example = "QWEN", allowableValues = {"QWEN", "DEEPSEEK", "OLLAMA", "LLAMA3"})
    @Pattern(regexp = "^(QWEN|DEEPSEEK|OLLAMA|LLAMA3|qwen|deepseek|ollama|llama3)?$",
            message = "provider must be QWEN, DEEPSEEK, OLLAMA or LLAMA3")
    private String provider;

    @Schema(description = "LLM model name. Uses configured default model if empty.", example = "qwen-turbo")
    private String model;

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
