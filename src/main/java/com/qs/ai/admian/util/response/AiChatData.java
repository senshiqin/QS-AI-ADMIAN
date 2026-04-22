package com.qs.ai.admian.util.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI chat response data.
 */
@Builder
public record AiChatData(
        String conversationId,
        String content,
        Integer totalTokens,
        List<String> references,
        LocalDateTime responseTime
) {
}
