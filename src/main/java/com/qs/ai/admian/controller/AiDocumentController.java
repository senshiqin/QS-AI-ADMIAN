package com.qs.ai.admian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qs.ai.admian.controller.response.AiDocumentBatchUploadResponse;
import com.qs.ai.admian.controller.response.AiDocumentDeleteResponse;
import com.qs.ai.admian.controller.response.AiDocumentPageResponse;
import com.qs.ai.admian.controller.response.AiDocumentResponse;
import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.entity.AiKnowledgeFile;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiKnowledgeFileService;
import com.qs.ai.admian.service.FileUploadService;
import com.qs.ai.admian.service.RagService;
import com.qs.ai.admian.util.MilvusVectorUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Knowledge document management APIs.
 */
@Slf4j
@Tag(name = "AI Documents", description = "Knowledge document query, delete and batch upload APIs")
@RestController
@RequestMapping("/api/v1/ai/documents")
@RequiredArgsConstructor
public class AiDocumentController {

    private static final long MAX_PAGE_SIZE = 100L;

    private final AiKnowledgeFileService aiKnowledgeFileService;
    private final FileUploadService fileUploadService;
    private final RagService ragService;
    private final MilvusVectorUtil milvusVectorUtil;

    @Operation(
            summary = "Page knowledge documents",
            description = "Query ai_knowledge_file by knowledge base, file name, file type, parse status and created time."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<AiDocumentPageResponse> pageDocuments(
            @Parameter(description = "Page number from 1", example = "1")
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Long pageNo,
            @Parameter(description = "Page size, max 100", example = "10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
            @Parameter(description = "Knowledge base code", example = "default")
            @RequestParam(value = "kbCode", required = false) String kbCode,
            @Parameter(description = "File name keyword", example = "multi_model")
            @RequestParam(value = "fileName", required = false) String fileName,
            @Parameter(description = "File type", example = "md")
            @RequestParam(value = "fileType", required = false) String fileType,
            @Parameter(description = "Parse status: 0 pending, 1 running, 2 success, 3 failed", example = "2")
            @RequestParam(value = "parseStatus", required = false) Integer parseStatus,
            @Parameter(description = "Created from, format yyyy-MM-dd HH:mm:ss")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            @RequestParam(value = "createdFrom", required = false) LocalDateTime createdFrom,
            @Parameter(description = "Created to, format yyyy-MM-dd HH:mm:ss")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            @RequestParam(value = "createdTo", required = false) LocalDateTime createdTo) {
        long safePageNo = pageNo == null || pageNo <= 0 ? 1L : pageNo;
        long safePageSize = pageSize == null || pageSize <= 0 ? 10L : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<AiKnowledgeFile> page = aiKnowledgeFileService.page(
                Page.of(safePageNo, safePageSize),
                buildQuery(kbCode, fileName, fileType, parseStatus, createdFrom, createdTo)
        );
        return ApiResponse.success(new AiDocumentPageResponse(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                page.getRecords().stream()
                        .map(this::toResponse)
                        .toList()
        ));
    }

    @Operation(summary = "Get knowledge document detail")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{fileId}")
    public ApiResponse<AiDocumentResponse> getDocument(
            @Parameter(description = "Document id", required = true, example = "1")
            @PathVariable Long fileId) {
        AiKnowledgeFile file = requireDocument(fileId);
        return ApiResponse.success(toResponse(file));
    }

    @Operation(
            summary = "Delete knowledge document",
            description = "Logic delete metadata and delete related vectors from Milvus. Physical file deletion is optional."
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{fileId}")
    public ApiResponse<AiDocumentDeleteResponse> deleteDocument(
            @Parameter(description = "Document id", required = true, example = "1")
            @PathVariable Long fileId,
            @Parameter(description = "Whether to delete local physical file", example = "false")
            @RequestParam(value = "deletePhysicalFile", required = false, defaultValue = "false")
            Boolean deletePhysicalFile) {
        AiKnowledgeFile file = requireDocument(fileId);
        long deletedVectorCount = milvusVectorUtil.deleteByFileId(fileId);
        boolean physicalDeleted = deletePhysicalFile(file, Boolean.TRUE.equals(deletePhysicalFile));

        file.setDeleted(1);
        file.setUpdateTime(LocalDateTime.now());
        boolean metadataDeleted = aiKnowledgeFileService.updateById(file);

        return ApiResponse.success("Document deleted", new AiDocumentDeleteResponse(
                fileId,
                metadataDeleted,
                deletedVectorCount,
                physicalDeleted
        ));
    }

    @Operation(
            summary = "Batch upload knowledge documents",
            description = "Upload pdf/docx/txt/md files and submit RAG parse, chunk, embed and Milvus ingest tasks. Async by default."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/batch-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AiDocumentBatchUploadResponse> batchUpload(
            @Parameter(
                    description = "Files to upload. Supported: pdf/docx/txt/md",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))
                    )
            )
            @RequestPart("files") List<MultipartFile> files,
            @Parameter(description = "Knowledge base code", example = "default")
            @RequestParam(value = "kbCode", required = false, defaultValue = "default") String kbCode,
            @Parameter(description = "Chunk size", example = "800")
            @RequestParam(value = "chunkSize", required = false, defaultValue = "800") Integer chunkSize,
            @Parameter(description = "Chunk overlap ratio", example = "0.15")
            @RequestParam(value = "overlapRatio", required = false, defaultValue = "0.15") Double overlapRatio,
            @Parameter(description = "Whether to ingest asynchronously", example = "true")
            @RequestParam(value = "async", required = false, defaultValue = "true") Boolean async,
            HttpServletRequest request) {
        if (files == null || files.isEmpty()) {
            throw new ParamException("files must not be empty");
        }

        List<FileUploadResponse> uploadedFiles = fileUploadService.uploadMultiple(files);
        List<RagIngestResponse> ingestResponses = uploadedFiles.stream()
                .map(file -> Boolean.FALSE.equals(async)
                        ? ragService.ingestFile(file, kbCode, resolveLoginUserId(request), chunkSize, overlapRatio)
                        : ragService.submitIngestFileAsync(file, kbCode, resolveLoginUserId(request), chunkSize, overlapRatio))
                .toList();
        return ApiResponse.success("Documents uploaded", new AiDocumentBatchUploadResponse(
                ingestResponses.size(),
                !Boolean.FALSE.equals(async),
                ingestResponses
        ));
    }

    private LambdaQueryWrapper<AiKnowledgeFile> buildQuery(String kbCode,
                                                           String fileName,
                                                           String fileType,
                                                           Integer parseStatus,
                                                           LocalDateTime createdFrom,
                                                           LocalDateTime createdTo) {
        LambdaQueryWrapper<AiKnowledgeFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiKnowledgeFile::getDeleted, 0);
        wrapper.eq(StringUtils.hasText(kbCode), AiKnowledgeFile::getKbCode, kbCode);
        wrapper.like(StringUtils.hasText(fileName), AiKnowledgeFile::getFileName, fileName);
        wrapper.eq(StringUtils.hasText(fileType), AiKnowledgeFile::getFileType, fileType);
        wrapper.eq(parseStatus != null, AiKnowledgeFile::getParseStatus, parseStatus);
        wrapper.ge(createdFrom != null, AiKnowledgeFile::getCreateTime, createdFrom);
        wrapper.le(createdTo != null, AiKnowledgeFile::getCreateTime, createdTo);
        wrapper.orderByDesc(AiKnowledgeFile::getCreateTime);
        return wrapper;
    }

    private AiKnowledgeFile requireDocument(Long fileId) {
        if (fileId == null) {
            throw new ParamException("fileId must not be null");
        }
        AiKnowledgeFile file = aiKnowledgeFileService.getOne(new LambdaQueryWrapper<AiKnowledgeFile>()
                .eq(AiKnowledgeFile::getId, fileId)
                .eq(AiKnowledgeFile::getDeleted, 0)
                .last("LIMIT 1"));
        if (file == null) {
            throw new ParamException("document not found: " + fileId);
        }
        return file;
    }

    private boolean deletePhysicalFile(AiKnowledgeFile file, boolean deletePhysicalFile) {
        if (!deletePhysicalFile || !StringUtils.hasText(file.getStoragePath())) {
            return false;
        }
        try {
            return Files.deleteIfExists(Path.of(file.getStoragePath()).toAbsolutePath().normalize());
        } catch (Exception ex) {
            log.warn("Failed to delete physical document file, fileId={}, path={}",
                    file.getId(), file.getStoragePath(), ex);
            return false;
        }
    }

    private AiDocumentResponse toResponse(AiKnowledgeFile file) {
        return new AiDocumentResponse(
                file.getId(),
                file.getKbCode(),
                file.getFileName(),
                file.getFileType(),
                file.getFileSize(),
                file.getStoragePath(),
                file.getFileHash(),
                file.getParseStatus(),
                file.getChunkCount(),
                file.getEmbeddingModel(),
                file.getVectorIndexName(),
                file.getLastParseTime(),
                file.getUploaderUserId(),
                file.getRemark(),
                file.getCreateTime(),
                file.getUpdateTime()
        );
    }

    private Long resolveLoginUserId(HttpServletRequest request) {
        Object loginUserId = request.getAttribute("loginUserId");
        if (loginUserId == null) {
            return 0L;
        }
        return Long.valueOf(String.valueOf(loginUserId));
    }
}
