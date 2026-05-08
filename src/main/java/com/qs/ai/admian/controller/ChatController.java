package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.ChatSendRequest;
import com.qs.ai.admian.controller.response.ChatSendResponse;
import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiChatRecordService;
import com.qs.ai.admian.service.QwenChatService;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.QwenChatResult;
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
import java.util.ArrayList;
import java.util.List;
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
    private final QwenChatService qwenChatService;

    @Operation(
            summary = "Send chat message",
            description = "Requires JWT token. Calls Qwen API with non-streaming response and saves messages into ai_chat_record."
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
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : "qwen-turbo";
        Double temperature = request.getTemperature() != null ? request.getTemperature() : 0.7D;
        List<AiChatMessage> messages = resolveMessages(request);
        AiChatMessage lastUserMessage = findLastUserMessage(messages);

        long startTime = System.currentTimeMillis();
        QwenChatResult chatResult = qwenChatService.chat(model, messages, temperature);
        int latencyMs = Math.toIntExact(Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startTime));

        AiChatRecord userMessage = new AiChatRecord();
        userMessage.setConversationId(conversationId);
        userMessage.setUserId(Long.valueOf(loginUserId));
        userMessage.setRoleType("user");
        userMessage.setContent(lastUserMessage.getContent());
        userMessage.setModelName(model);
        userMessage.setPromptTokens(defaultInt(chatResult.promptTokens()));
        userMessage.setCompletionTokens(0);
        userMessage.setTotalTokens(defaultInt(chatResult.promptTokens()));
        userMessage.setDeleted(0);
        userMessage.setCreateTime(LocalDateTime.now());
        userMessage.setUpdateTime(LocalDateTime.now());
        aiChatRecordService.save(userMessage);

        AiChatRecord assistantMessage = new AiChatRecord();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setUserId(Long.valueOf(loginUserId));
        assistantMessage.setRoleType("assistant");
        assistantMessage.setContent(chatResult.answer());
        assistantMessage.setModelName(model);
        assistantMessage.setPromptTokens(defaultInt(chatResult.promptTokens()));
        assistantMessage.setCompletionTokens(defaultInt(chatResult.completionTokens()));
        assistantMessage.setTotalTokens(defaultInt(chatResult.totalTokens()));
        assistantMessage.setLatencyMs(latencyMs);
        assistantMessage.setRequestId(chatResult.requestId());
        assistantMessage.setDeleted(0);
        assistantMessage.setCreateTime(LocalDateTime.now());
        assistantMessage.setUpdateTime(LocalDateTime.now());
        aiChatRecordService.save(assistantMessage);

        ChatSendResponse response = ChatSendResponse.builder()
                .conversationId(conversationId)
                .modelType(model)
                .answer(chatResult.answer())
                .totalTokens(defaultInt(chatResult.totalTokens()))
                .build();
        return ApiResponse.success("Chat success", response);
    }

    private List<AiChatMessage> resolveMessages(ChatSendRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            return request.getMessages();
        }
        if (StringUtils.hasText(request.getContent())) {
            List<AiChatMessage> messages = new ArrayList<>();
            messages.add(AiChatMessage.builder()
                    .role("user")
                    .content(request.getContent())
                    .build());
            return messages;
        }
        throw new ParamException("messages must not be empty, or content must not be blank");
    }

    private AiChatMessage findLastUserMessage(List<AiChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessage message = messages.get(i);
            if ("user".equals(message.getRole())) {
                return message;
            }
        }
        throw new ParamException("messages must contain at least one user message");
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
