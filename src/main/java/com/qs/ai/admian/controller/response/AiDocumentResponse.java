package com.qs.ai.admian.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Knowledge document metadata.
 */
@Schema(description = "知识库文档元数据")
public record AiDocumentResponse(
        @Schema(description = "文档ID", example = "1")
        Long id,
        @Schema(description = "知识库编码", example = "default")
        String kbCode,
        @Schema(description = "原始文件名", example = "knowledge.md")
        String fileName,
        @Schema(description = "文件类型", example = "md")
        String fileType,
        @Schema(description = "文件大小，单位字节", example = "2048")
        Long fileSize,
        @Schema(description = "本地存储路径")
        String storagePath,
        @Schema(description = "文件 SHA-256 哈希")
        String fileHash,
        @Schema(description = "解析状态：0待处理，1处理中，2成功，3失败", example = "2")
        Integer parseStatus,
        @Schema(description = "切片数量", example = "12")
        Integer chunkCount,
        @Schema(description = "Embedding 模型", example = "text-embedding-v4")
        String embeddingModel,
        @Schema(description = "向量索引名称", example = "milvus:1")
        String vectorIndexName,
        @Schema(description = "最近解析时间")
        LocalDateTime lastParseTime,
        @Schema(description = "上传用户ID", example = "1")
        Long uploaderUserId,
        @Schema(description = "备注，失败时通常保存错误信息")
        String remark,
        @Schema(description = "创建时间")
        LocalDateTime createTime,
        @Schema(description = "更新时间")
        LocalDateTime updateTime
) {
}
