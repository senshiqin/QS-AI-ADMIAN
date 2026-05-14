package com.qs.ai.admian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagIngestTaskPageResponse;
import com.qs.ai.admian.controller.response.RagIngestTaskResponse;
import com.qs.ai.admian.entity.AiRagIngestTask;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiRagIngestTaskService;
import com.qs.ai.admian.service.RagIngestTaskRetryService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG ingest task status and retry APIs.
 */
@Tag(name = "AI RAG Tasks", description = "RAG ingest task status query and retry APIs")
@RestController
@RequestMapping("/api/v1/ai/rag/tasks")
@RequiredArgsConstructor
public class RagIngestTaskController {

    private static final long MAX_PAGE_SIZE = 100L;

    private final AiRagIngestTaskService aiRagIngestTaskService;
    private final RagIngestTaskRetryService ragIngestTaskRetryService;

    @Operation(summary = "Page RAG ingest tasks")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<RagIngestTaskPageResponse> pageTasks(
            @Parameter(description = "Page number from 1", example = "1")
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Long pageNo,
            @Parameter(description = "Page size, max 100", example = "10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
            @Parameter(description = "Task status: PENDING/RUNNING/SUCCESS/FAILED", example = "FAILED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Knowledge file id", example = "1")
            @RequestParam(value = "fileId", required = false) Long fileId,
            @Parameter(description = "Knowledge base code", example = "default")
            @RequestParam(value = "kbCode", required = false) String kbCode) {
        long safePageNo = pageNo == null || pageNo <= 0 ? 1L : pageNo;
        long safePageSize = pageSize == null || pageSize <= 0 ? 10L : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<AiRagIngestTask> page = aiRagIngestTaskService.page(
                Page.of(safePageNo, safePageSize),
                buildQuery(status, fileId, kbCode)
        );
        return ApiResponse.success(new RagIngestTaskPageResponse(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                page.getRecords().stream().map(this::toResponse).toList()
        ));
    }

    @Operation(summary = "Get RAG ingest task detail")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{taskId}")
    public ApiResponse<RagIngestTaskResponse> getTask(
            @Parameter(description = "Task id", required = true, example = "1")
            @PathVariable Long taskId) {
        return ApiResponse.success(toResponse(requireTask(taskId)));
    }

    @Operation(summary = "Retry failed RAG ingest task")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{taskId}/retry")
    public ApiResponse<RagIngestResponse> retryTask(
            @Parameter(description = "Failed task id", required = true, example = "1")
            @PathVariable Long taskId) {
        return ApiResponse.success("RAG ingest retry submitted", ragIngestTaskRetryService.retry(taskId));
    }

    private LambdaQueryWrapper<AiRagIngestTask> buildQuery(String status, Long fileId, String kbCode) {
        LambdaQueryWrapper<AiRagIngestTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), AiRagIngestTask::getStatus, normalizeStatus(status));
        wrapper.eq(fileId != null, AiRagIngestTask::getKnowledgeFileId, fileId);
        wrapper.eq(StringUtils.hasText(kbCode), AiRagIngestTask::getKbCode, kbCode);
        wrapper.orderByDesc(AiRagIngestTask::getCreateTime);
        return wrapper;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(java.util.Locale.ROOT) : status;
    }

    private AiRagIngestTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new ParamException("taskId must not be null");
        }
        AiRagIngestTask task = aiRagIngestTaskService.getById(taskId);
        if (task == null) {
            throw new ParamException("RAG ingest task not found: " + taskId);
        }
        return task;
    }

    private RagIngestTaskResponse toResponse(AiRagIngestTask task) {
        return new RagIngestTaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getKnowledgeFileId(),
                task.getKbCode(),
                task.getFileName(),
                task.getStoragePath(),
                task.getStatus(),
                task.getProgressPercent(),
                task.getCurrentStep(),
                task.getRetryCount(),
                task.getMaxRetry(),
                task.getChunkSize(),
                task.getOverlapRatio(),
                task.getTextLength(),
                task.getChunkCount(),
                task.getStoredVectorCount(),
                task.getEmbeddingModel(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getDurationMs(),
                task.getCreateTime(),
                task.getUpdateTime()
        );
    }
}
