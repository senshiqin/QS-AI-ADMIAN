package com.qs.ai.admian.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.DashScopeProperties;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.QwenChatService;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.QwenChatResult;
import com.qs.ai.admian.service.dto.QwenStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Override
    public QwenChatResult streamChat(String model,
                                     List<AiChatMessage> messages,
                                     Double temperature,
                                     QwenStreamHandler streamHandler) {
        if (!StringUtils.hasText(dashScopeProperties.getApiKey())) {
            throw new AiApiException("DashScope API key is not configured. Please set DASHSCOPE_API_KEY.");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", StringUtils.hasText(model) ? model : DEFAULT_MODEL);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature != null ? temperature : DEFAULT_TEMPERATURE);
        requestBody.put("stream", true);
        requestBody.put("stream_options", Map.of("include_usage", true));

        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(buildChatUrl()))
                    .timeout(Duration.ofMillis(dashScopeProperties.getReadTimeoutMs()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + dashScopeProperties.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(dashScopeProperties.getConnectTimeoutMs()))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = lines.collect(Collectors.joining("\n"));
                    throw new AiApiException("Qwen stream API failed, status=" + response.statusCode() + ", body=" + errorBody);
                }
                return parseStreamLines(lines, streamHandler);
            }
        } catch (AiApiException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("Qwen stream interrupted, possible client disconnect", ex);
            throw new AiApiException("Qwen stream interrupted: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiApiException("Qwen stream request interrupted");
        } catch (Exception ex) {
            log.error("Qwen stream API request failed", ex);
            throw new AiApiException("Qwen stream API request failed: " + ex.getMessage());
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

    private QwenChatResult parseStreamLines(Stream<String> lines, QwenStreamHandler streamHandler) {
        StringBuilder answerBuilder = new StringBuilder();
        StreamAccumulator accumulator = new StreamAccumulator();

        lines.forEach(line -> {
            if (!StringUtils.hasText(line) || !line.startsWith("data:")) {
                return;
            }
            String data = line.substring("data:".length()).trim();
            if ("[DONE]".equals(data)) {
                return;
            }
            parseStreamData(data, streamHandler, answerBuilder, accumulator);
        });

        return QwenChatResult.builder()
                .answer(answerBuilder.toString())
                .requestId(accumulator.requestId)
                .promptTokens(accumulator.promptTokens)
                .completionTokens(accumulator.completionTokens)
                .totalTokens(accumulator.totalTokens)
                .build();
    }

    private void parseStreamData(String data,
                                 QwenStreamHandler streamHandler,
                                 StringBuilder answerBuilder,
                                 StreamAccumulator accumulator) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if (!StringUtils.hasText(accumulator.requestId)) {
                accumulator.requestId = root.path("id").asText(null);
            }

            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                accumulator.promptTokens = asNullableInt(usage.path("prompt_tokens"));
                accumulator.completionTokens = asNullableInt(usage.path("completion_tokens"));
                accumulator.totalTokens = asNullableInt(usage.path("total_tokens"));
            }

            String content = root.path("choices").path(0).path("delta").path("content").asText();
            if (StringUtils.hasText(content)) {
                answerBuilder.append(content);
                streamHandler.onContent(content);
            }
        } catch (IOException ex) {
            throw new StreamClientDisconnectedException(ex);
        } catch (Exception ex) {
            log.warn("Failed to parse one Qwen stream chunk, data={}", data, ex);
            throw new AiApiException("Failed to parse Qwen stream response");
        }
    }

    private String buildChatUrl() {
        String baseUrl = dashScopeProperties.getBaseUrl();
        String chatPath = dashScopeProperties.getChatPath();
        if (baseUrl.endsWith("/") && chatPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + chatPath;
        }
        if (!baseUrl.endsWith("/") && !chatPath.startsWith("/")) {
            return baseUrl + "/" + chatPath;
        }
        return baseUrl + chatPath;
    }

    private static class StreamAccumulator {
        private String requestId;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    private static class StreamClientDisconnectedException extends RuntimeException {

        private StreamClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
