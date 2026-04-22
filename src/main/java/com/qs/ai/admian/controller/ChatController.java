package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.AiChatRequest;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.util.response.AiResponseFactory;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI chat APIs.
 */
@Tag(name = "ChatController", description = "AI对话接口")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Operation(
            summary = "发送对话消息",
            description = "接收用户消息并返回AI回复，支持deepseek和tongyi模型。"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "调用成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "4001",
                    description = "参数校验失败",
                    content = @Content(
                            examples = @ExampleObject(value = "{\"code\":4001,\"message\":\"modelType must be deepseek or tongyi\",\"data\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "5102",
                    description = "AI接口调用失败",
                    content = @Content(
                            examples = @ExampleObject(value = "{\"code\":5102,\"message\":\"AI model upstream call failed\",\"data\":null}")
                    )
            )
    })
    @PostMapping("/send")
    public ApiResponse<?> sendMessage(@RequestBody @Valid AiChatRequest request) {
        if ("api_fail".equalsIgnoreCase(request.getContent())) {
            throw new AiApiException("AI model upstream call failed");
        }
        return AiResponseFactory.chatSuccess(
                request.getUserId(),
                "Model [" + request.getModelType() + "] response: " + request.getContent(),
                180,
                List.of("doc://chat-api", "doc://model/" + request.getModelType())
        );
    }
}
