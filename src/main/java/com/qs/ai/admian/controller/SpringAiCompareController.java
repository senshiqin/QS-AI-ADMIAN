package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.SpringAiChatRequest;
import com.qs.ai.admian.controller.response.SpringAiChatResponse;
import com.qs.ai.admian.controller.response.SpringAiCompareResponse;
import com.qs.ai.admian.service.SpringAiCompareService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring AI comparison and adapter demo APIs.
 */
@Tag(name = "AI Spring AI Compare", description = "Spring AI comparison and lightweight adapter demo APIs")
@RestController
@RequestMapping("/api/v1/ai/spring-ai")
@RequiredArgsConstructor
public class SpringAiCompareController {

    private final SpringAiCompareService springAiCompareService;

    @Operation(summary = "Compare current AI architecture with Spring AI concepts")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/compare")
    public ApiResponse<SpringAiCompareResponse> compare() {
        return ApiResponse.success("Spring AI comparison generated", springAiCompareService.compare());
    }

    @Operation(summary = "Run a Spring AI style chat demo through the existing multi-model adapter")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/chat")
    public ApiResponse<SpringAiChatResponse> chat(@Valid @RequestBody SpringAiChatRequest request) {
        return ApiResponse.success("Spring AI style chat completed", springAiCompareService.chat(request));
    }
}
