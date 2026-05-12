package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * LangChain4j embedding demo response.
 */
public record LangChain4jEmbedResponse(
        String model,
        Integer dimension,
        List<Float> preview
) {
}
