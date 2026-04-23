package com.qs.ai.admian.controller.response;

import lombok.Builder;

/**
 * Chat send response.
 */
@Builder
public record ChatSendResponse(
        String conversationId,
        String modelType,
        String answer,
        Integer totalTokens
) {
}
