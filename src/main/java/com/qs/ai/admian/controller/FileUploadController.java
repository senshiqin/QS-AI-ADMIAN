package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.service.FileUploadService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * File upload APIs.
 */
@Tag(name = "AI Files", description = "File upload APIs for AI knowledge ingestion")
@RestController
@RequestMapping("/api/v1/ai/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @Operation(
            summary = "Upload single file",
            description = "Requires JWT token. Supports pdf, docx, txt and md. Max size is 100MB per file."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadSingle(
            @Parameter(description = "File to upload, allowed extensions: pdf/docx/txt/md")
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("File uploaded", fileUploadService.uploadSingle(file));
    }

    @Operation(
            summary = "Upload multiple files",
            description = "Requires JWT token. Supports pdf, docx, txt and md. Max size is 100MB per file."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/batch-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<FileUploadResponse>> uploadMultiple(
            @Parameter(description = "Files to upload, allowed extensions: pdf/docx/txt/md")
            @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.success("Files uploaded", fileUploadService.uploadMultiple(files));
    }
}
