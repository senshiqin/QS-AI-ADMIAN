package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.FileVectorizeChunkResponse;
import com.qs.ai.admian.controller.response.FileVectorizeResponse;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.FileUploadService;
import com.qs.ai.admian.service.dto.TextChunk;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.MilvusVectorUtil;
import com.qs.ai.admian.util.TextChunkUtil;
import com.qs.ai.admian.util.TextParseUtil;
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

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final double DEFAULT_OVERLAP_RATIO = 0.10D;
    private static final int DEFAULT_PREVIEW_LIMIT = 3;
    private static final int MAX_PREVIEW_LIMIT = 10;
    private static final int PREVIEW_LENGTH = 120;

    private final FileUploadService fileUploadService;
    private final AiEmbeddingUtil aiEmbeddingUtil;
    private final MilvusVectorUtil milvusVectorUtil;

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

    @Operation(
            summary = "Upload and vectorize file",
            description = "Requires JWT token. Uploads one file, parses text, splits semantic chunks and calls DashScope Embedding API."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload-and-vectorize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileVectorizeResponse> uploadAndVectorize(
            @Parameter(description = "File to upload and vectorize, allowed extensions: pdf/docx/txt/md")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "File ID used in generated chunks. For manual tests, keep default 0.")
            @RequestParam(value = "fileId", required = false, defaultValue = "0") Long fileId,
            @Parameter(description = "Chunk size, default 800")
            @RequestParam(value = "chunkSize", required = false, defaultValue = "800") Integer chunkSize,
            @Parameter(description = "Overlap ratio, default 0.1")
            @RequestParam(value = "overlapRatio", required = false, defaultValue = "0.1") Double overlapRatio,
            @Parameter(description = "Number of chunk previews returned, default 3, max 10")
            @RequestParam(value = "previewLimit", required = false, defaultValue = "3") Integer previewLimit,
            @Parameter(description = "Whether to store vectors into Milvus after embedding")
            @RequestParam(value = "storeToMilvus", required = false, defaultValue = "false") Boolean storeToMilvus) {
        FileUploadResponse uploadResponse = fileUploadService.uploadSingle(file);
        String parsedText = TextParseUtil.parse(uploadResponse.storagePath());
        List<TextChunk> chunks = TextChunkUtil.splitBySemantic(
                fileId,
                parsedText,
                chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize,
                overlapRatio == null ? DEFAULT_OVERLAP_RATIO : overlapRatio
        );
        if (chunks.isEmpty()) {
            throw new ParamException("parsed text does not contain valid chunks");
        }

        List<String> chunkTexts = chunks.stream()
                .map(TextChunk::content)
                .toList();
        List<float[]> vectors = aiEmbeddingUtil.embedBatch(chunkTexts);
        int embeddingDimension = vectors.isEmpty() ? 0 : vectors.get(0).length;
        int safePreviewLimit = resolvePreviewLimit(previewLimit);
        long storedVectorCount = Boolean.TRUE.equals(storeToMilvus)
                ? milvusVectorUtil.batchInsert(chunks, vectors)
                : 0L;

        List<FileVectorizeChunkResponse> chunkPreviews = buildChunkPreviews(chunks, vectors, safePreviewLimit);
        FileVectorizeResponse response = new FileVectorizeResponse(
                uploadResponse.originalFilename(),
                uploadResponse.storedFilename(),
                uploadResponse.storagePath(),
                fileId,
                parsedText.length(),
                chunks.size(),
                aiEmbeddingUtil.getEmbeddingModel(),
                embeddingDimension,
                Boolean.TRUE.equals(storeToMilvus),
                storedVectorCount,
                chunkPreviews
        );
        return ApiResponse.success("File parsed and vectorized", response);
    }

    private List<FileVectorizeChunkResponse> buildChunkPreviews(List<TextChunk> chunks,
                                                               List<float[]> vectors,
                                                               int previewLimit) {
        int limit = Math.min(previewLimit, chunks.size());
        return java.util.stream.IntStream.range(0, limit)
                .mapToObj(index -> {
                    TextChunk chunk = chunks.get(index);
                    float[] vector = vectors.get(index);
                    return new FileVectorizeChunkResponse(
                            chunk.chunkId(),
                            chunk.fileId(),
                            chunk.chunkIndex(),
                            chunk.startOffset(),
                            chunk.endOffset(),
                            chunk.content().length(),
                            preview(chunk.content()),
                            vector.length
                    );
                })
                .toList();
    }

    private int resolvePreviewLimit(Integer previewLimit) {
        if (previewLimit == null || previewLimit <= 0) {
            return DEFAULT_PREVIEW_LIMIT;
        }
        return Math.min(previewLimit, MAX_PREVIEW_LIMIT);
    }

    private String preview(String content) {
        if (content == null || content.length() <= PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_LENGTH);
    }
}
