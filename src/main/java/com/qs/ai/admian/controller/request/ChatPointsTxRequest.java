package com.qs.ai.admian.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request for transactional chat + points update demo.
 */
@Data
public class ChatPointsTxRequest {

    @Schema(description = "User id", example = "1")
    @NotNull(message = "userId must not be null")
    private Long userId;

    @Schema(description = "Chat content", example = "Give me a summary of RAG.")
    @NotBlank(message = "content must not be blank")
    private String content;

    @Schema(description = "Points to add", example = "10")
    @NotNull(message = "pointsDelta must not be null")
    @Min(value = 1, message = "pointsDelta must be greater than 0")
    private Integer pointsDelta;

    @Schema(description = "Model name", example = "deepseek")
    private String modelName = "deepseek";

    @Schema(description = "Set true to simulate points update failure and verify rollback", example = "false")
    private Boolean simulatePointUpdateFail = false;
}
