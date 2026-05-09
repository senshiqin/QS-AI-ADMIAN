package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.CopywritingGenerateRequest;
import com.qs.ai.admian.controller.response.CopywritingGenerateResponse;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.util.AiApiUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.StringJoiner;

/**
 * General AI application APIs.
 */
@Tag(name = "AI Tools", description = "General AI application APIs")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private static final String DEFAULT_QWEN_MODEL = "qwen-turbo";

    private final AiApiUtil aiApiUtil;

    @Operation(
            summary = "Generate copywriting",
            description = "Requires JWT token. Uses Qwen API and returns structured JSON copywriting result."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/copywriting/generate")
    public ApiResponse<CopywritingGenerateResponse> generateCopywriting(
            @RequestBody @Valid CopywritingGenerateRequest request) {
        List<AiChatMessage> messages = List.of(
                AiChatMessage.builder()
                        .role("system")
                        .content(buildSystemPrompt())
                        .build(),
                AiChatMessage.builder()
                        .role("user")
                        .content(buildUserPrompt(request))
                        .build()
        );

        CopywritingGenerateResponse response = aiApiUtil.structuredChat(
                AiModelProvider.QWEN,
                messages,
                AiChatOptions.builder()
                        .model(StringUtils.hasText(request.getModel()) ? request.getModel() : DEFAULT_QWEN_MODEL)
                        .temperature(request.getTemperature())
                        .maxTokens(1024)
                        .maxInputTokens(3000)
                        .build(),
                CopywritingGenerateResponse.class
        );

        return ApiResponse.success("Copywriting generated", response);
    }

    private String buildSystemPrompt() {
        return """
                You are a professional Chinese marketing copywriter.
                Generate practical, concise and commercially usable Chinese copywriting.
                Return only valid JSON. Do not return Markdown, comments, explanations or extra text.
                The JSON field names must be exactly:
                {
                  "title": "string, within 24 Chinese characters",
                  "subtitle": "string, within 50 Chinese characters",
                  "body": "string",
                  "sellingPoints": ["string", "string", "string"],
                  "callToAction": "string, within 12 Chinese characters",
                  "tags": ["string"]
                }
                Rules:
                - sellingPoints must contain exactly 3 items.
                - tags must contain 3 to 5 items.
                - Do not use exaggerated absolute claims such as best, No.1, 100%, forever.
                - If information is insufficient, generate a safe generic version based on the keywords.
                """;
    }

    private String buildUserPrompt(CopywritingGenerateRequest request) {
        return """
                Copywriting requirements:
                copyType: %s
                keywords: %s
                targetAudience: %s
                tone: %s
                length: %s
                """.formatted(
                request.getCopyType(),
                joinKeywords(request.getKeywords()),
                StringUtils.hasText(request.getTargetAudience()) ? request.getTargetAudience() : "general users",
                StringUtils.hasText(request.getTone()) ? request.getTone() : "professional",
                StringUtils.hasText(request.getLength()) ? request.getLength() : "medium"
        );
    }

    private String joinKeywords(List<String> keywords) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String keyword : keywords) {
            joiner.add(keyword);
        }
        return joiner.toString();
    }
}
