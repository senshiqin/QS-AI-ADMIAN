package com.qs.ai.admian.controller;

import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.util.response.AiKnowledgeData;
import com.qs.ai.admian.util.response.AiResponseFactory;
import com.qs.ai.admian.util.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Demo AI APIs with unified response.
 */
@RestController
@RequestMapping("/api/ai")
@Validated
public class AiDemoController {

    @GetMapping("/chat")
    public ApiResponse<?> chat(@RequestParam(defaultValue = "default-conversation") String conversationId,
                               @RequestParam @NotBlank(message = "prompt must not be blank") String prompt) {
        if ("api_fail".equalsIgnoreCase(prompt)) {
            throw new AiApiException("AI model upstream call failed");
        }
        if ("param_error".equalsIgnoreCase(prompt)) {
            throw new ParamException("AI request parameter is invalid");
        }
        if ("npe".equalsIgnoreCase(prompt)) {
            throw new NullPointerException("Simulated NPE");
        }
        return AiResponseFactory.chatSuccess(
                conversationId,
                "已收到你的问题: " + prompt,
                128,
                List.of("doc://ai-intro", "doc://kb-guide")
        );
    }

    @GetMapping("/knowledge")
    public ApiResponse<?> knowledge(@RequestParam @NotBlank(message = "question must not be blank") String question) {
        List<AiKnowledgeData.KnowledgeItem> items = List.of(
                AiKnowledgeData.KnowledgeItem.builder()
                        .docId("kb-001")
                        .title("RAG 基础")
                        .snippet("RAG 通过检索外部知识增强大模型回答。")
                        .score(0.97D)
                        .build(),
                AiKnowledgeData.KnowledgeItem.builder()
                        .docId("kb-002")
                        .title("向量检索")
                        .snippet("向量数据库可支持语义相似度搜索。")
                        .score(0.92D)
                        .build()
        );
        return AiResponseFactory.knowledgeSuccess(
                question,
                items,
                "已返回最相关的知识库条目，可用于生成最终答案。"
        );
    }
}
