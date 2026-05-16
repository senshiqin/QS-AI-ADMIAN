package com.qs.ai.admian.controller.response;

import java.util.List;

/**
 * Spring AI comparison response for interview and architecture review.
 */
public record SpringAiCompareResponse(
        String currentBootVersion,
        String springAiCompatibility,
        String projectChoice,
        List<CompareItem> items,
        List<String> migrationSteps
) {

    public record CompareItem(
            String topic,
            String currentProject,
            String springAi,
            String interviewPoint
    ) {
    }
}
