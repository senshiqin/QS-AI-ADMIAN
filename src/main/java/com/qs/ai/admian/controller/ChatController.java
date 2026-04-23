package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.ChatSendRequest;
import com.qs.ai.admian.controller.response.ChatSendResponse;
import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.service.AiChatRecordService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI chat APIs.
 */
@Tag(name = "AI Chat", description = "AI chat APIs")
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiChatRecordService aiChatRecordService;

    @Operation(
            summary = "Send chat message",
            description = "Requires JWT token. Save user and assistant messages into ai_chat_record."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized, token missing or invalid",
                    content = @Content(
                            examples = @ExampleObject(value = "{\"code\":401,\"message\":\"User not logged in or token missing\",\"data\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden, token expired",
                    content = @Content(
                            examples = @ExampleObject(value = "{\"code\":403,\"message\":\"Token expired, please login again\",\"data\":null}")
                    )
            )
    })
    @PostMapping("/send")
    public ApiResponse<ChatSendResponse> sendMessage(@RequestBody @Valid ChatSendRequest request,
                                                     HttpServletRequest httpServletRequest) {
        String loginUserId = String.valueOf(httpServletRequest.getAttribute("loginUserId"));
        String conversationId = StringUtils.hasText(request.getConversationId())
                ? request.getConversationId() : "conv-" + UUID.randomUUID().toString().replace("-", "");

        String answer = "Model [" + request.getModelType() + "] response: " + request.getContent();
        int promptTokens = Math.max(1, request.getContent().length() / 2);
        int completionTokens = 120;
        int totalTokens = promptTokens + completionTokens;

        AiChatRecord userMessage = new AiChatRecord();
        userMessage.setConversationId(conversationId);
        userMessage.setUserId(Long.valueOf(loginUserId));
        userMessage.setRoleType("user");
        userMessage.setContent(request.getContent());
        userMessage.setModelName(request.getModelType());
        userMessage.setPromptTokens(promptTokens);
        userMessage.setCompletionTokens(0);
        userMessage.setTotalTokens(promptTokens);
        userMessage.setDeleted(0);
        userMessage.setCreateTime(LocalDateTime.now());
        userMessage.setUpdateTime(LocalDateTime.now());
        aiChatRecordService.save(userMessage);

        AiChatRecord assistantMessage = new AiChatRecord();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setUserId(Long.valueOf(loginUserId));
        assistantMessage.setRoleType("assistant");
        assistantMessage.setContent(answer);
        assistantMessage.setModelName(request.getModelType());
        assistantMessage.setPromptTokens(promptTokens);
        assistantMessage.setCompletionTokens(completionTokens);
        assistantMessage.setTotalTokens(totalTokens);
        assistantMessage.setLatencyMs(180);
        assistantMessage.setDeleted(0);
        assistantMessage.setCreateTime(LocalDateTime.now());
        assistantMessage.setUpdateTime(LocalDateTime.now());
        aiChatRecordService.save(assistantMessage);

        ChatSendResponse response = ChatSendResponse.builder()
                .conversationId(conversationId)
                .modelType(request.getModelType())
                .answer(answer)
                .totalTokens(totalTokens)
                .build();
        return ApiResponse.success("Chat success", response);
    }
}
