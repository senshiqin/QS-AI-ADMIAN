package com.qs.ai.admian.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Knowledge document delete result.
 */
@Schema(description = "知识库文档删除响应")
public record AiDocumentDeleteResponse(
        @Schema(description = "文档ID", example = "1")
        Long fileId,
        @Schema(description = "是否删除文档元数据", example = "true")
        Boolean metadataDeleted,
        @Schema(description = "删除的向量数量", example = "12")
        Long deletedVectorCount,
        @Schema(description = "是否尝试删除本地物理文件", example = "false")
        Boolean physicalFileDeleted
) {
}
