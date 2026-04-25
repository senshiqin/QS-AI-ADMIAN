package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.ChatContextAddRequest;
import com.qs.ai.admian.service.ChatContextService;
import com.qs.ai.admian.service.dto.ChatContextMessage;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Swagger test APIs for chat context management.
 */
@Tag(name = "Chat Context", description = "Redis List based chat context APIs")
@Validated
@RestController
@RequestMapping("/api/v1/ai/context")
@RequiredArgsConstructor
public class ChatContextController {

    private final ChatContextService chatContextService;

    @Operation(summary = "Add context message", description = "Push one message into Redis List and set expire to 1 hour")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(
                            examples = @ExampleObject(value = "{\"code\":401,\"message\":\"User not logged in or token missing\",\"data\":null}")
                    )
            )
    })
    @PostMapping("/messages")
    public ApiResponse<Void> addMessage(@RequestBody @Valid ChatContextAddRequest request) {
        chatContextService.addContextMessage(
                request.getUserId(),
                request.getConversationId(),
                request.getRole(),
                request.getContent()
        );
        return ApiResponse.<Void>success("Context message added", null);
    }

    @Operation(summary = "Get context messages", description = "Read full context list from Redis")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/messages")
    public ApiResponse<List<ChatContextMessage>> getMessages(
            @Parameter(description = "User id", example = "1")
            @RequestParam @NotNull(message = "userId must not be null") Long userId,
            @Parameter(description = "Conversation id", example = "conv-001")
            @RequestParam @NotBlank(message = "conversationId must not be blank") String conversationId) {
        return ApiResponse.success(chatContextService.getContextMessages(userId, conversationId));
    }

    @Operation(summary = "Clear context messages", description = "Delete one conversation context list from Redis")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/messages")
    public ApiResponse<Boolean> clearMessages(
            @Parameter(description = "User id", example = "1")
            @RequestParam @NotNull(message = "userId must not be null") Long userId,
            @Parameter(description = "Conversation id", example = "conv-001")
            @RequestParam @NotBlank(message = "conversationId must not be blank") String conversationId) {
        return ApiResponse.success(chatContextService.clearContext(userId, conversationId));
    }
}
