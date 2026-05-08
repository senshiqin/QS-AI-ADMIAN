package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.ChatSendRequest;
import com.qs.ai.admian.controller.response.ChatSendResponse;
import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiChatRecordService;
import com.qs.ai.admian.service.ChatContextService;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.dto.ChatContextMessage;
import com.qs.ai.admian.util.AiApiUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI chat APIs.
 */
@Slf4j
@Tag(name = "AI Chat", description = "AI chat APIs")
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String DEFAULT_MODEL = "qwen-turbo";
    private static final double DEFAULT_TEMPERATURE = 0.7D;
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final AiChatRecordService aiChatRecordService;
    private final ChatContextService chatContextService;
    private final AiApiUtil aiApiUtil;

    @Operation(
            summary = "Send chat message",
            description = "Requires JWT token. Auto routes to Qwen or DeepSeek by provider/model and saves messages into ai_chat_record."
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
        String conversationId = resolveConversationId(request);
        AiModelProvider provider = resolveProvider(request);
        String model = resolveModel(request);
        Double temperature = resolveTemperature(request);
        Long userId = Long.valueOf(loginUserId);
        List<AiChatMessage> currentMessages = resolveMessages(request);
        List<AiChatMessage> messages = buildMessagesWithContext(userId, conversationId, currentMessages);
        AiChatMessage lastUserMessage = findLastUserMessage(currentMessages);

        long startTime = System.currentTimeMillis();
        saveUserMessage(conversationId, userId, lastUserMessage.getContent(), model, 0);
        try {
            AiApiChatResult chatResult = aiApiUtil.chat(
                    provider,
                    messages,
                    AiChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .maxTokens(1024)
                            .maxInputTokens(4000)
                            .build()
            );
            int latencyMs = elapsedMs(startTime);

            saveAssistantMessage(conversationId, userId, chatResult, model, latencyMs, null);
            addContextPair(userId, conversationId, lastUserMessage.getContent(), chatResult.answer());

            ChatSendResponse response = ChatSendResponse.builder()
                    .conversationId(conversationId)
                    .modelType(model)
                    .answer(chatResult.answer())
                    .totalTokens(defaultInt(chatResult.totalTokens()))
                    .build();
            return ApiResponse.success("Chat success", response);
        } catch (RuntimeException ex) {
            saveErrorAssistantMessage(conversationId, userId, model, elapsedMs(startTime), ex);
            throw ex;
        }
    }

    @Operation(
            summary = "Stream chat message",
            description = "Requires JWT token. Calls Qwen stream API and pushes answer fragments to client by SSE."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody @Valid ChatSendRequest request,
                                    HttpServletRequest httpServletRequest) {
        String loginUserId = String.valueOf(httpServletRequest.getAttribute("loginUserId"));
        Long userId = Long.valueOf(loginUserId);
        String conversationId = resolveConversationId(request);
        AiModelProvider provider = resolveProvider(request);
        String model = resolveModel(request);
        Double temperature = resolveTemperature(request);
        List<AiChatMessage> currentMessages = resolveMessages(request);
        List<AiChatMessage> messages = buildMessagesWithContext(userId, conversationId, currentMessages);
        AiChatMessage lastUserMessage = findLastUserMessage(currentMessages);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> closed.set(true));

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                saveUserMessage(conversationId, userId, lastUserMessage.getContent(), model, 0);
                AiApiChatResult result = aiApiUtil.streamChat(
                        provider,
                        messages,
                        AiChatOptions.builder()
                                .model(model)
                                .temperature(temperature)
                                .maxTokens(1024)
                                .maxInputTokens(4000)
                                .build(),
                        content -> {
                    sendCharacters(emitter, closed, content);
                });
                saveAssistantMessage(conversationId, userId, result, model, elapsedMs(startTime), null);
                addContextPair(userId, conversationId, lastUserMessage.getContent(), result.answer());
                sendEvent(emitter, closed, "done", "[DONE]");
                completeEmitter(emitter, closed);
            } catch (Exception ex) {
                handleStreamError(emitter, closed, conversationId, userId, model, elapsedMs(startTime), ex);
            }
        });

        return emitter;
    }

    private void sendCharacters(SseEmitter emitter, AtomicBoolean closed, String content) throws IOException {
        int index = 0;
        while (index < content.length()) {
            int codePoint = content.codePointAt(index);
            String character = new String(Character.toChars(codePoint));
            sendEvent(emitter, closed, "message", character);
            index += Character.charCount(codePoint);
        }
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean closed, String eventName, String data) throws IOException {
        if (closed.get()) {
            throw new IOException("SSE connection already closed");
        }
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private void handleStreamError(SseEmitter emitter,
                                   AtomicBoolean closed,
                                   String conversationId,
                                   Long userId,
                                   String model,
                                   int latencyMs,
                                   Exception ex) {
        if (closed.get()) {
            log.info("SSE client disconnected, conversationId={}", conversationId);
            return;
        }

        log.error("SSE chat stream failed, conversationId={}", conversationId, ex);
        saveErrorAssistantMessage(conversationId, userId, model, latencyMs, ex);
        try {
            sendEvent(emitter, closed, "error", ex.getMessage());
        } catch (IOException sendError) {
            log.info("Failed to send SSE error event, conversationId={}", conversationId, sendError);
        } finally {
            completeEmitter(emitter, closed);
        }
    }

    private String resolveConversationId(ChatSendRequest request) {
        return StringUtils.hasText(request.getConversationId())
                ? request.getConversationId() : "conv-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveModel(ChatSendRequest request) {
        if (StringUtils.hasText(request.getModel())) {
            return request.getModel();
        }
        return resolveProvider(request) == AiModelProvider.DEEPSEEK ? "deepseek-chat" : DEFAULT_MODEL;
    }

    private AiModelProvider resolveProvider(ChatSendRequest request) {
        if (StringUtils.hasText(request.getProvider())) {
            return AiModelProvider.valueOf(request.getProvider().toUpperCase());
        }
        String model = request.getModel();
        if (StringUtils.hasText(model) && model.toLowerCase().startsWith("deepseek")) {
            return AiModelProvider.DEEPSEEK;
        }
        return AiModelProvider.QWEN;
    }

    private Double resolveTemperature(ChatSendRequest request) {
        return request.getTemperature() != null ? request.getTemperature() : DEFAULT_TEMPERATURE;
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

    private List<AiChatMessage> buildMessagesWithContext(Long userId,
                                                         String conversationId,
                                                         List<AiChatMessage> currentMessages) {
        List<AiChatMessage> messages = new ArrayList<>();
        List<ChatContextMessage> contextMessages = chatContextService.getContextMessages(userId, conversationId);
        for (ChatContextMessage contextMessage : contextMessages) {
            if (StringUtils.hasText(contextMessage.getRole()) && StringUtils.hasText(contextMessage.getContent())) {
                messages.add(AiChatMessage.builder()
                        .role(contextMessage.getRole())
                        .content(contextMessage.getContent())
                        .build());
            }
        }
        messages.addAll(currentMessages);
        return messages;
    }

    private void addContextPair(Long userId, String conversationId, String userContent, String assistantContent) {
        chatContextService.addContextMessage(userId, conversationId, "user", userContent);
        if (StringUtils.hasText(assistantContent)) {
            chatContextService.addContextMessage(userId, conversationId, "assistant", assistantContent);
        }
    }

    private void saveUserMessage(String conversationId, Long userId, String content, String model, int promptTokens) {
        AiChatRecord userMessage = new AiChatRecord();
        userMessage.setConversationId(conversationId);
        userMessage.setUserId(userId);
        userMessage.setRoleType("user");
        userMessage.setContent(content);
        userMessage.setModelName(model);
        userMessage.setPromptTokens(promptTokens);
        userMessage.setCompletionTokens(0);
        userMessage.setTotalTokens(promptTokens);
        userMessage.setDeleted(0);
        userMessage.setCreateTime(LocalDateTime.now());
        userMessage.setUpdateTime(LocalDateTime.now());
        aiChatRecordService.save(userMessage);
    }

    private void saveAssistantMessage(String conversationId,
                                      Long userId,
                                      AiApiChatResult chatResult,
                                      String model,
                                      int latencyMs,
                                      String errorMessage) {
        AiChatRecord assistantMessage = new AiChatRecord();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setUserId(userId);
        assistantMessage.setRoleType("assistant");
        assistantMessage.setContent(chatResult.answer());
        assistantMessage.setModelName(model);
        assistantMessage.setPromptTokens(defaultInt(chatResult.promptTokens()));
        assistantMessage.setCompletionTokens(defaultInt(chatResult.completionTokens()));
        assistantMessage.setTotalTokens(defaultInt(chatResult.totalTokens()));
        assistantMessage.setLatencyMs(latencyMs);
        assistantMessage.setRequestId(chatResult.requestId());
        assistantMessage.setErrorMessage(errorMessage);
        assistantMessage.setDeleted(0);
        assistantMessage.setCreateTime(LocalDateTime.now());
        assistantMessage.setUpdateTime(LocalDateTime.now());
        aiChatRecordService.save(assistantMessage);
    }

    private void saveErrorAssistantMessage(String conversationId,
                                           Long userId,
                                           String model,
                                           int latencyMs,
                                           Exception ex) {
        AiApiChatResult errorResult = AiApiChatResult.builder()
                .answer("")
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .build();
        saveAssistantMessage(conversationId, userId, errorResult, model, latencyMs, ex.getMessage());
    }

    private int elapsedMs(long startTime) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startTime));
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
