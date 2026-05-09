package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Structured copywriting generation response.
 */
public record CopywritingGenerateResponse(
        String title,
        String subtitle,
        String body,
        List<String> sellingPoints,
        String callToAction,
        List<String> tags
) {
}
