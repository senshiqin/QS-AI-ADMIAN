package com.qs.ai.admian.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.DashScopeProperties;
import com.qs.ai.admian.config.DeepSeekProperties;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.AiApiService;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.dto.AiStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic AI API utility for OpenAI-compatible providers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiApiUtil {

    private static final String DEFAULT_QWEN_MODEL = "qwen-turbo";
    private static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
    private static final double DEFAULT_TEMPERATURE = 0.7D;

    private final DashScopeProperties dashScopeProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final ObjectMapper objectMapper;
    private final AiApiService aiApiService;

    public AiApiChatResult chat(AiModelProvider provider,
                                List<AiChatMessage> messages,
                                AiChatOptions options) {
        ProviderConfig config = resolveProviderConfig(provider);
        aiApiService.validateApiKey(config.apiKey(), config.providerName());
        aiApiService.validateInputTokens(messages, options == null ? null : options.maxInputTokens());

        Map<String, Object> requestBody = buildRequestBody(config, messages, options, false, false);
        try {
            HttpRequest request = buildRequest(config, requestBody, MediaType.APPLICATION_JSON_VALUE);
            HttpClient httpClient = buildHttpClient(config);
            HttpResponse<String> response = aiApiService.executeWithRetry(
                    config.providerName() + " non-stream chat",
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            );
            ensureSuccess(config.providerName(), response.statusCode(), response.body());
            return parseChatResponse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw aiApiService.toAiApiException(config.providerName() + " non-stream chat", ex);
        } catch (Exception ex) {
            throw aiApiService.toAiApiException(config.providerName() + " non-stream chat", ex);
        }
    }

    public AiApiChatResult streamChat(AiModelProvider provider,
                                      List<AiChatMessage> messages,
                                      AiChatOptions options,
                                      AiStreamHandler streamHandler) {
        ProviderConfig config = resolveProviderConfig(provider);
        aiApiService.validateApiKey(config.apiKey(), config.providerName());
        aiApiService.validateInputTokens(messages, options == null ? null : options.maxInputTokens());

        Map<String, Object> requestBody = buildRequestBody(config, messages, options, true, false);
        try {
            HttpRequest request = buildRequest(config, requestBody, MediaType.TEXT_EVENT_STREAM_VALUE);
            HttpClient httpClient = buildHttpClient(config);
            HttpResponse<Stream<String>> response = aiApiService.executeWithRetry(
                    config.providerName() + " stream chat",
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
            );
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = lines.collect(Collectors.joining("\n"));
                    throw new AiApiException(config.providerName() + " stream API failed, status="
                            + response.statusCode() + ", body=" + errorBody);
                }
                return parseStreamResponse(lines, streamHandler);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw aiApiService.toAiApiException(config.providerName() + " stream chat", ex);
        } catch (Exception ex) {
            throw aiApiService.toAiApiException(config.providerName() + " stream chat", ex);
        }
    }

    public <T> T structuredChat(AiModelProvider provider,
                                List<AiChatMessage> messages,
                                AiChatOptions options,
                                Class<T> responseType) {
        ProviderConfig config = resolveProviderConfig(provider);
        List<AiChatMessage> jsonMessages = appendJsonOutputInstruction(messages);
        Map<String, Object> requestBody = buildRequestBody(config, jsonMessages, options, false, true);

        aiApiService.validateApiKey(config.apiKey(), config.providerName());
        aiApiService.validateInputTokens(jsonMessages, options == null ? null : options.maxInputTokens());
        try {
            HttpRequest request = buildRequest(config, requestBody, MediaType.APPLICATION_JSON_VALUE);
            HttpClient httpClient = buildHttpClient(config);
            HttpResponse<String> response = aiApiService.executeWithRetry(
                    config.providerName() + " structured chat",
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            );
            ensureSuccess(config.providerName(), response.statusCode(), response.body());
            AiApiChatResult result = parseChatResponse(response.body());
            return objectMapper.readValue(cleanJson(result.answer()), responseType);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse structured AI response as {}", responseType.getSimpleName(), ex);
            throw new AiApiException("AI structured response is not valid JSON");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw aiApiService.toAiApiException(config.providerName() + " structured chat", ex);
        } catch (Exception ex) {
            throw aiApiService.toAiApiException(config.providerName() + " structured chat", ex);
        }
    }

    private Map<String, Object> buildRequestBody(ProviderConfig config,
                                                 List<AiChatMessage> messages,
                                                 AiChatOptions options,
                                                 boolean stream,
                                                 boolean structuredOutput) {
        AiChatOptions safeOptions = options == null ? AiChatOptions.builder().build() : options;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", StringUtils.hasText(safeOptions.model()) ? safeOptions.model() : config.defaultModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", safeOptions.temperature() != null ? safeOptions.temperature() : DEFAULT_TEMPERATURE);
        requestBody.put("stream", stream);
        if (safeOptions.maxTokens() != null) {
            requestBody.put("max_tokens", safeOptions.maxTokens());
        }
        if (stream) {
            requestBody.put("stream_options", Map.of("include_usage", true));
        }
        if (structuredOutput) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }
        return requestBody;
    }

    private HttpRequest buildRequest(ProviderConfig config, Map<String, Object> requestBody, String accept)
            throws JsonProcessingException {
        return HttpRequest.newBuilder()
                .uri(URI.create(config.chatUrl()))
                .timeout(Duration.ofMillis(config.readTimeoutMs()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, accept)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
    }

    private HttpClient buildHttpClient(ProviderConfig config) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();
    }

    private AiApiChatResult parseChatResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new AiApiException("AI API returned empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String answer = root.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(answer)) {
                throw new AiApiException("AI API response does not contain answer content");
            }
            JsonNode usage = root.path("usage");
            return AiApiChatResult.builder()
                    .answer(answer)
                    .requestId(root.path("id").asText(null))
                    .promptTokens(asNullableInt(usage.path("prompt_tokens")))
                    .completionTokens(asNullableInt(usage.path("completion_tokens")))
                    .totalTokens(asNullableInt(usage.path("total_tokens")))
                    .rawResponse(responseBody)
                    .build();
        } catch (AiApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse AI API response, body={}", responseBody, ex);
            throw new AiApiException("Failed to parse AI API response");
        }
    }

    private AiApiChatResult parseStreamResponse(Stream<String> lines, AiStreamHandler streamHandler) {
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

        return AiApiChatResult.builder()
                .answer(answerBuilder.toString())
                .requestId(accumulator.requestId)
                .promptTokens(accumulator.promptTokens)
                .completionTokens(accumulator.completionTokens)
                .totalTokens(accumulator.totalTokens)
                .build();
    }

    private void parseStreamData(String data,
                                 AiStreamHandler streamHandler,
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
            throw new StreamOutputException(ex);
        } catch (Exception ex) {
            log.warn("Failed to parse AI stream chunk, data={}", data, ex);
            throw new AiApiException("Failed to parse AI stream response");
        }
    }

    private void ensureSuccess(String providerName, int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new AiApiException(providerName + " API failed, status=" + statusCode + ", body=" + body);
        }
    }

    private List<AiChatMessage> appendJsonOutputInstruction(List<AiChatMessage> messages) {
        List<AiChatMessage> result = new ArrayList<>();
        result.add(AiChatMessage.builder()
                .role("system")
                .content("Only return valid JSON. Do not return Markdown, explanations, or extra text.")
                .build());
        if (messages != null) {
            result.addAll(messages);
        }
        return result;
    }

    private String cleanJson(String text) {
        if (text == null) {
            return "";
        }
        String result = text.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7).trim();
        }
        if (result.startsWith("```")) {
            result = result.substring(3).trim();
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3).trim();
        }
        return result;
    }

    private Integer asNullableInt(JsonNode node) {
        return node.isInt() ? node.asInt() : null;
    }

    private ProviderConfig resolveProviderConfig(AiModelProvider provider) {
        AiModelProvider safeProvider = provider == null ? AiModelProvider.QWEN : provider;
        return switch (safeProvider) {
            case QWEN -> new ProviderConfig(
                    "DashScope",
                    dashScopeProperties.getApiKey(),
                    buildUrl(dashScopeProperties.getBaseUrl(), dashScopeProperties.getChatPath()),
                    DEFAULT_QWEN_MODEL,
                    dashScopeProperties.getConnectTimeoutMs(),
                    dashScopeProperties.getReadTimeoutMs()
            );
            case DEEPSEEK -> new ProviderConfig(
                    "DeepSeek",
                    deepSeekProperties.getApiKey(),
                    buildUrl(deepSeekProperties.getBaseUrl(), deepSeekProperties.getChatPath()),
                    DEFAULT_DEEPSEEK_MODEL,
                    deepSeekProperties.getConnectTimeoutMs(),
                    deepSeekProperties.getReadTimeoutMs()
            );
        };
    }

    private String buildUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private record ProviderConfig(
            String providerName,
            String apiKey,
            String chatUrl,
            String defaultModel,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    private static class StreamAccumulator {
        private String requestId;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    private static class StreamOutputException extends RuntimeException {

        private StreamOutputException(Throwable cause) {
            super(cause);
        }
    }
}
