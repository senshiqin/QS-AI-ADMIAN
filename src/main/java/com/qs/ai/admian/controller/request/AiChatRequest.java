package com.qs.ai.admian.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * AI chat request parameters.
 */
@Data
public class AiChatRequest {

    @Schema(description = "对话内容", example = "你好，给我介绍一下RAG")
    @NotBlank(message = "content must not be blank")
    private String content;

    @Schema(description = "用户ID", example = "u1001")
    @NotBlank(message = "userId must not be blank")
    private String userId;

    @Schema(description = "模型类型，仅支持deepseek或tongyi", example = "deepseek")
    @NotBlank(message = "modelType must not be blank")
    @Pattern(regexp = "^(deepseek|tongyi)$", message = "modelType must be deepseek or tongyi")
    private String modelType;
}
