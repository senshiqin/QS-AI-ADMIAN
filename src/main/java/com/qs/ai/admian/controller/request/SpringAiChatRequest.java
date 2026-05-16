package com.qs.ai.admian.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * Request for Spring AI style chat demo.
 */
public record SpringAiChatRequest(
        @Schema(description = "User prompt", example = "Explain what Spring AI ChatClient does")
        @NotBlank(message = "prompt must not be blank")
        String prompt,

        @Schema(description = "Optional system prompt", example = "You are a concise Java backend interviewer")
        String systemPrompt,

        @Schema(description = "Provider key or alias", example = "QWEN")
        String provider,

        @Schema(description = "Model name. Uses configured default model if empty.", example = "qwen-turbo")
        String model,

        @Schema(description = "Sampling temperature", example = "0.3")
        @DecimalMin(value = "0.0", message = "temperature must be greater than or equal to 0")
        @DecimalMax(value = "2.0", message = "temperature must be less than or equal to 2")
        Double temperature
) {
}
