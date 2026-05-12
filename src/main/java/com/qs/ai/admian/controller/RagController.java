package com.qs.ai.admian.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.controller.request.RagAskRequest;
import com.qs.ai.admian.controller.request.RagRetrieveRequest;
import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.controller.response.RagAnswerResponse;
import com.qs.ai.admian.controller.response.RagIngestResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.FileUploadService;
import com.qs.ai.admian.service.RagService;
import com.qs.ai.admian.util.MultiModelChatUtil;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Core RAG workflow APIs.
 */
@Tag(name = "AI RAG", description = "Core RAG ingestion and retrieval APIs")
@RestController
@RequestMapping("/api/v1/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final FileUploadService fileUploadService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final MultiModelChatUtil multiModelChatUtil;
    @Qualifier("aiTaskExecutor")
    private final Executor aiTaskExecutor;

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

    @Operation(summary = "RAG question answering with Qwen streaming output")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody RagAskRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> closed.set(true));

        CompletableFuture.runAsync(() -> {
            try {
                RagRetrieveResponse retrieval = ragService.retrieve(request.queryText(), request.topK(), request.minScore());
                sendEvent(emitter, closed, "sources", retrieval.chunks());

                AiApiChatResult result = ragService.streamAnswer(
                        retrieval,
                        request.provider(),
                        request.model(),
                        request.temperature(),
                        content -> sendEvent(emitter, closed, "message", content)
                );
                RagAnswerResponse answerResponse = new RagAnswerResponse(
                        retrieval.queryText(),
                        result.answer(),
                        resolveProvider(request.provider()),
                        request.model() == null || request.model().isBlank()
                                ? multiModelChatUtil.defaultModel(request.provider())
                                : request.model(),
                        retrieval.hitCount(),
                        retrieval.chunks()
                );
                sendEvent(emitter, closed, "result", answerResponse);
                sendEvent(emitter, closed, "done", "[DONE]");
                completeEmitter(emitter, closed);
            } catch (Exception ex) {
                sendEvent(emitter, closed, "error", ex.getMessage());
                completeEmitter(emitter, closed);
            }
        }, aiTaskExecutor);

        return emitter;
    }

    private Long resolveLoginUserId(HttpServletRequest request) {
        Object loginUserId = request.getAttribute("loginUserId");
        if (loginUserId == null) {
            return 0L;
        }
        return Long.valueOf(String.valueOf(loginUserId));
    }

    private String resolveProvider(String provider) {
        return provider == null || provider.isBlank() ? "qwen" : provider.trim().toLowerCase();
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean closed, String eventName, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            Object payload = data instanceof String ? data : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload));
        } catch (IOException ex) {
            closed.set(true);
        }
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }
}
