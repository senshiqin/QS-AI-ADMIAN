package com.qs.ai.admian.util.response;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Factory methods for AI responses.
 */
public final class AiResponseFactory {

    private AiResponseFactory() {
        throw new IllegalStateException("Utility class");
    }

    public static ApiResponse<AiChatData> chatSuccess(String conversationId, String content, Integer totalTokens,
                                                      List<String> references) {
        AiChatData data = AiChatData.builder()
                .conversationId(conversationId)
                .content(content)
                .totalTokens(totalTokens)
                .references(references == null ? Collections.emptyList() : references)
                .responseTime(LocalDateTime.now())
                .build();
        return ApiResponse.success("AI response success", data);
    }

    public static ApiResponse<AiKnowledgeData> knowledgeSuccess(String question,
                                                                List<AiKnowledgeData.KnowledgeItem> items,
                                                                String summary) {
        AiKnowledgeData data = AiKnowledgeData.builder()
                .question(question)
                .items(items == null ? Collections.emptyList() : items)
                .summary(summary)
                .build();
        return ApiResponse.success("Knowledge query success", data);
    }

    public static <T> ApiResponse<T> aiFail(String message) {
        return ApiResponse.fail(ResultCode.AI_SERVICE_ERROR.getCode(), message);
    }
}
