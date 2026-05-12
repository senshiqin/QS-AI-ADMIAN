package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * LangChain4j chat demo request.
 */
public record LangChain4jChatRequest(
        String systemPrompt,
        @NotBlank(message = "message must not be blank")
        String message,
        String model,
        Double temperature
) {
}
