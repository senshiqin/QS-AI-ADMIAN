package com.qs.ai.admian.controller.response;

import lombok.Builder;

/**
 * Response for transactional chat + points update demo.
 */
@Builder
public record ChatPointsTxResponse(
        Long userId,
        Long chatRecordId,
        Integer beforePoints,
        Integer afterPoints,
        String conversationId
) {
}
