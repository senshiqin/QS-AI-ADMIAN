package com.qs.ai.admian.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.OllamaProperties;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Ollama local chat API utility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaChatUtil {

    private static final String PROVIDER_NAME = "Ollama";

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    public AiApiChatResult chat(List<AiChatMessage> messages, AiChatOptions options) {
        Map<String, Object> requestBody = buildRequestBody(messages, options, false);
        try {
            HttpResponse<String> response = buildHttpClient()
                    .send(buildRequest(requestBody), HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            JsonNode root = objectMapper.readTree(response.body());
            return AiApiChatResult.builder()
                    .answer(root.path("message").path("content").asText())
                    .requestId(root.path("created_at").asText(null))
                    .rawResponse(response.body())
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiApiException(PROVIDER_NAME + " chat interrupted");
        } catch (Exception ex) {
            log.error("Ollama chat failed", ex);
            throw new AiApiException(PROVIDER_NAME + " chat failed: " + ex.getMessage());
        }
    }

    public AiApiChatResult streamChat(List<AiChatMessage> messages,
                                      AiChatOptions options,
                                      Consumer<String> contentConsumer) {
        Map<String, Object> requestBody = buildRequestBody(messages, options, true);
        StringBuilder answerBuilder = new StringBuilder();
        try {
            HttpResponse<Stream<String>> response = buildHttpClient()
                    .send(buildRequest(requestBody), HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = String.join("\n", lines.toList());
                    throw new AiApiException(PROVIDER_NAME + " stream chat failed, status="
                            + response.statusCode() + ", body=" + errorBody);
                }
                lines.forEach(line -> handleStreamLine(line, contentConsumer, answerBuilder));
            }
            return AiApiChatResult.builder()
                    .answer(answerBuilder.toString())
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiApiException(PROVIDER_NAME + " stream chat interrupted");
        } catch (Exception ex) {
            log.error("Ollama stream chat failed", ex);
            throw new AiApiException(PROVIDER_NAME + " stream chat failed: " + ex.getMessage());
        }
    }

    public String defaultModel() {
        return properties.getModel();
    }

    private void handleStreamLine(String line, Consumer<String> contentConsumer, StringBuilder answerBuilder) {
        if (!StringUtils.hasText(line)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(line);
            String content = root.path("message").path("content").asText();
            if (StringUtils.hasText(content)) {
                answerBuilder.append(content);
                contentConsumer.accept(content);
            }
        } catch (Exception ex) {
            throw new AiApiException("Failed to parse Ollama stream response");
        }
    }

    private Map<String, Object> buildRequestBody(List<AiChatMessage> messages,
                                                 AiChatOptions options,
                                                 boolean stream) {
        AiChatOptions safeOptions = options == null ? AiChatOptions.builder().build() : options;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", StringUtils.hasText(safeOptions.model()) ? safeOptions.model() : properties.getModel());
        requestBody.put("messages", messages);
        requestBody.put("stream", stream);
        requestBody.put("keep_alive", properties.getKeepAlive());

        Map<String, Object> modelOptions = new HashMap<>();
        modelOptions.put("temperature", safeOptions.temperature());
        modelOptions.put("num_predict", safeOptions.maxTokens() == null ? properties.getNumPredict() : safeOptions.maxTokens());
        modelOptions.put("num_ctx", properties.getNumCtx());
        if (properties.getNumThread() != null && properties.getNumThread() > 0) {
            modelOptions.put("num_thread", properties.getNumThread());
        }
        requestBody.put("options", modelOptions);
        return requestBody;
    }

    private HttpRequest buildRequest(Map<String, Object> requestBody) throws Exception {
        return HttpRequest.newBuilder()
                .uri(URI.create(buildUrl()))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    private String buildUrl() {
        String baseUrl = properties.getBaseUrl();
        String path = properties.getChatPath();
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private void ensureSuccess(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new AiApiException(PROVIDER_NAME + " API failed, status=" + statusCode + ", body=" + body);
        }
    }
}
