package com.qs.ai.admian.controller.response;

import lombok.Builder;

/**
 * Login response body.
 */
@Builder
public record LoginResponse(
        String userId,
        String username,
        String tokenType,
        String accessToken,
        Long expiresInSeconds
) {
}
