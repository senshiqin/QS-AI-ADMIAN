package com.qs.ai.admian.controller;

import com.qs.ai.admian.config.MilvusProperties;
import com.qs.ai.admian.controller.request.VectorSearchRequest;
import com.qs.ai.admian.controller.request.VectorUpsertRequest;
import com.qs.ai.admian.controller.response.VectorCollectionResponse;
import com.qs.ai.admian.controller.response.VectorWriteResponse;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.MilvusVectorService;
import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.service.dto.MilvusVectorRecord;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Milvus vector CRUD APIs.
 */
@Tag(name = "AI Vectors", description = "Milvus vector CRUD APIs for RAG")
@RestController
@RequestMapping("/api/v1/ai/vectors")
@RequiredArgsConstructor
public class VectorController {

    private static final int DEFAULT_TOP_K = 5;

    private final MilvusVectorService milvusVectorService;
    private final MilvusProperties milvusProperties;
    private final AiEmbeddingUtil aiEmbeddingUtil;

    @Operation(summary = "Create Milvus collection if absent")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/collection")
    public ApiResponse<VectorCollectionResponse> createCollection() {
        milvusVectorService.createCollectionIfAbsent();
        return ApiResponse.success("Milvus collection is ready", collectionResponse());
    }

    @Operation(summary = "Get Milvus collection status")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/collection")
    public ApiResponse<VectorCollectionResponse> collectionStatus() {
        return ApiResponse.success(collectionResponse());
    }

    @Operation(summary = "Upsert one vector record")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/upsert")
    public ApiResponse<VectorWriteResponse> upsert(@Valid @RequestBody VectorUpsertRequest request) {
        long affectedCount = milvusVectorService.upsert(new MilvusVectorRecord(
                request.chunkId(),
                request.fileId(),
                request.chunkIndex(),
                request.content(),
                toFloatArray(request.vector())
        ));
        return ApiResponse.success("Vector record upserted", new VectorWriteResponse(affectedCount));
    }

    @Operation(summary = "Search vectors by query text or raw vector")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/search")
    public ApiResponse<List<MilvusSearchResult>> search(@RequestBody VectorSearchRequest request) {
        float[] queryVector = resolveQueryVector(request);
        int topK = request.topK() == null || request.topK() <= 0 ? DEFAULT_TOP_K : request.topK();
        return ApiResponse.success(milvusVectorService.search(queryVector, topK));
    }

    @Operation(summary = "Query vector records by file id")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/files/{fileId}")
    public ApiResponse<List<MilvusSearchResult>> queryByFileId(@PathVariable Long fileId,
                                                               @RequestParam(defaultValue = "100") Integer limit) {
        return ApiResponse.success(milvusVectorService.queryByFileId(fileId, limit));
    }

    @Operation(summary = "Delete vector record by chunk id")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/chunks/{chunkId}")
    public ApiResponse<VectorWriteResponse> deleteByChunkId(@PathVariable String chunkId) {
        long affectedCount = milvusVectorService.deleteByChunkId(chunkId);
        return ApiResponse.success("Vector record deleted", new VectorWriteResponse(affectedCount));
    }

    @Operation(summary = "Delete vector records by file id")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/files/{fileId}")
    public ApiResponse<VectorWriteResponse> deleteByFileId(@PathVariable Long fileId) {
        long affectedCount = milvusVectorService.deleteByFileId(fileId);
        return ApiResponse.success("Vector records deleted", new VectorWriteResponse(affectedCount));
    }

    private VectorCollectionResponse collectionResponse() {
        return new VectorCollectionResponse(
                milvusProperties.getCollectionName(),
                milvusProperties.getDimension(),
                milvusVectorService.hasCollection()
        );
    }

    private float[] resolveQueryVector(VectorSearchRequest request) {
        if (request == null) {
            throw new ParamException("request body must not be null");
        }
        if (StringUtils.hasText(request.queryText())) {
            return aiEmbeddingUtil.embed(request.queryText());
        }
        if (request.vector() != null && !request.vector().isEmpty()) {
            return toFloatArray(request.vector());
        }
        throw new ParamException("queryText or vector must not be empty");
    }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Float value = values.get(i);
            if (value == null) {
                throw new ParamException("vector[" + i + "] must not be null");
            }
            result[i] = value;
        }
        return result;
    }
}
