package com.qs.ai.admian.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.DashScopeProperties;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.QwenChatService;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.QwenChatResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls DashScope OpenAI-compatible chat completions API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenChatServiceImpl implements QwenChatService {

    private static final String DEFAULT_MODEL = "qwen-turbo";
    private static final double DEFAULT_TEMPERATURE = 0.7D;

    private final RestClient dashScopeRestClient;
    private final DashScopeProperties dashScopeProperties;
    private final ObjectMapper objectMapper;

    @Override
    public QwenChatResult chat(String model, List<AiChatMessage> messages, Double temperature) {
        if (!StringUtils.hasText(dashScopeProperties.getApiKey())) {
            throw new AiApiException("DashScope API key is not configured. Please set DASHSCOPE_API_KEY.");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", StringUtils.hasText(model) ? model : DEFAULT_MODEL);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature != null ? temperature : DEFAULT_TEMPERATURE);
        requestBody.put("stream", false);

        try {
            ResponseEntity<String> responseEntity = dashScopeRestClient.post()
                    .uri(dashScopeProperties.getChatPath())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + dashScopeProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);

            return parseResponse(responseEntity.getBody());
        } catch (RestClientException ex) {
            log.error("Qwen API request failed", ex);
            throw new AiApiException("Qwen API request failed: " + ex.getMessage());
        }
    }

    private QwenChatResult parseResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new AiApiException("Qwen API returned empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageNode = root.path("choices").path(0).path("message");
            String answer = messageNode.path("content").asText();
            if (!StringUtils.hasText(answer)) {
                throw new AiApiException("Qwen API response does not contain answer content");
            }

            JsonNode usage = root.path("usage");
            return QwenChatResult.builder()
                    .answer(answer)
                    .requestId(root.path("id").asText(null))
                    .promptTokens(asNullableInt(usage.path("prompt_tokens")))
                    .completionTokens(asNullableInt(usage.path("completion_tokens")))
                    .totalTokens(asNullableInt(usage.path("total_tokens")))
                    .build();
        } catch (AiApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse Qwen API response, body={}", responseBody, ex);
            throw new AiApiException("Failed to parse Qwen API response");
        }
    }

    private Integer asNullableInt(JsonNode node) {
        return node.isInt() ? node.asInt() : null;
    }
}
