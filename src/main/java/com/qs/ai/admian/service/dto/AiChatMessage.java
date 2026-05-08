package com.qs.ai.admian.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM chat message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {

    @Schema(description = "Message role", example = "user")
    @NotBlank(message = "role must not be blank")
    @Pattern(regexp = "^(system|user|assistant)$", message = "role must be system, user or assistant")
    private String role;

    @Schema(description = "Message content", example = "你好，介绍一下 RAG")
    @NotBlank(message = "message content must not be blank")
    private String content;
}
