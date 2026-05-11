package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.RagRetrieveRequest;
import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.service.FileUploadService;
import com.qs.ai.admian.service.RagService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Core RAG workflow APIs.
 */
@Tag(name = "AI RAG", description = "Core RAG ingestion and retrieval APIs")
@RestController
@RequestMapping("/api/v1/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final FileUploadService fileUploadService;
    private final RagService ragService;

    @Operation(summary = "Upload, parse, chunk, embed and store vectors into Milvus")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RagIngestResponse> ingest(
            @Parameter(description = "File to ingest, allowed extensions: pdf/docx/txt/md")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Knowledge base code, default is default")
            @RequestParam(value = "kbCode", required = false, defaultValue = "default") String kbCode,
            @Parameter(description = "Chunk size, default 800")
            @RequestParam(value = "chunkSize", required = false, defaultValue = "800") Integer chunkSize,
            @Parameter(description = "Overlap ratio, default 0.1")
            @RequestParam(value = "overlapRatio", required = false, defaultValue = "0.1") Double overlapRatio,
            HttpServletRequest request) {
        FileUploadResponse uploadResponse = fileUploadService.uploadSingle(file);
        RagIngestResponse response = ragService.ingestFile(
                uploadResponse,
                kbCode,
                resolveLoginUserId(request),
                chunkSize,
                overlapRatio
        );
        return ApiResponse.success("RAG file ingested", response);
    }

    @Operation(summary = "Embed query text and retrieve Top5 similar chunks from Milvus")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/retrieve")
    public ApiResponse<RagRetrieveResponse> retrieve(@Valid @RequestBody RagRetrieveRequest request) {
        RagRetrieveResponse response = ragService.retrieve(request.queryText(), request.topK(), request.minScore());
        return ApiResponse.success("RAG chunks retrieved", response);
    }

    private Long resolveLoginUserId(HttpServletRequest request) {
        Object loginUserId = request.getAttribute("loginUserId");
        if (loginUserId == null) {
            return 0L;
        }
        return Long.valueOf(String.valueOf(loginUserId));
    }
}
