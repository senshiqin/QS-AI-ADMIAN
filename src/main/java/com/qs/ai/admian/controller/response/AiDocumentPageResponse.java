package com.qs.ai.admian.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Paged knowledge document response.
 */
@Schema(description = "知识库文档分页响应")
public record AiDocumentPageResponse(
        @Schema(description = "当前页码", example = "1")
        Long pageNo,
        @Schema(description = "每页大小", example = "10")
        Long pageSize,
        @Schema(description = "总记录数", example = "42")
        Long total,
        @Schema(description = "总页数", example = "5")
        Long pages,
        @Schema(description = "文档列表")
        List<AiDocumentResponse> records
) {
}
