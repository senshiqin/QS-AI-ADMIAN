package com.qs.ai.admian.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Batch knowledge document upload result.
 */
@Schema(description = "知识库文档批量上传响应")
public record AiDocumentBatchUploadResponse(
        @Schema(description = "提交文件数量", example = "3")
        Integer submittedCount,
        @Schema(description = "是否异步向量化", example = "true")
        Boolean async,
        @Schema(description = "入库任务结果列表")
        List<RagIngestResponse> files
) {
}
