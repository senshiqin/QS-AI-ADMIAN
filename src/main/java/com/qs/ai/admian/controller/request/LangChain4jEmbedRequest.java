package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * LangChain4j embedding demo request.
 */
public record LangChain4jEmbedRequest(
        @NotBlank(message = "text must not be blank")
        String text
) {
}
