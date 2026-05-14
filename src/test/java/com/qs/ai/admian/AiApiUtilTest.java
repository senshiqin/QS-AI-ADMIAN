package com.qs.ai.admian;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.AiModelsProperties;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.service.AiApiService;
import com.qs.ai.admian.service.dto.AiApiChatResult;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.service.impl.AiApiServiceImpl;
import com.qs.ai.admian.util.AiApiUtil;
import com.qs.ai.admian.util.RedisUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;

/**
 * Verifies reusable AI API utility behavior across providers and output modes.
 */
class AiApiUtilTest {

    private HttpServer httpServer;
    private String baseUrl;
    private AiApiUtil aiApiUtil;
    private AiApiService aiApiService;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/qwen/chat", exchange -> {
            lastRequestBody.set(readBody(exchange.getRequestBody()));
            writeJson(exchange, """
                    {
                      "id": "qwen-request-001",
                      "choices": [
                        {"message": {"content": "qwen answer"}}
                      ],
                      "usage": {
                        "prompt_tokens": 6,
                        "completion_tokens": 3,
                        "total_tokens": 9
                      }
                    }
                    """);
        });
        httpServer.createContext("/deepseek/chat", exchange -> {
            lastRequestBody.set(readBody(exchange.getRequestBody()));
            writeJson(exchange, """
                    {
                      "id": "deepseek-request-001",
                      "choices": [
                        {"message": {"content": "deepseek answer"}}
                      ],
                      "usage": {
                        "prompt_tokens": 7,
                        "completion_tokens": 4,
                        "total_tokens": 11
                      }
                    }
                    """);
        });
        httpServer.createContext("/structured/chat", exchange -> {
            lastRequestBody.set(readBody(exchange.getRequestBody()));
            writeJson(exchange, """
                    {
                      "id": "structured-request-001",
                      "choices": [
                        {"message": {"content": "```json\\n{\\\"intent\\\":\\\"login\\\",\\\"confidence\\\":0.91}\\n```"}}
                      ]
                    }
                    """);
        });
        httpServer.createContext("/stream/chat", exchange -> {
            lastRequestBody.set(readBody(exchange.getRequestBody()));
            byte[] response = """
                    data: {"id":"stream-request-001","choices":[{"delta":{"content":"A"}}]}

                    data: {"id":"stream-request-001","choices":[{"delta":{"content":"I"}}],"usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        httpServer.start();
        baseUrl = "http://localhost:" + httpServer.getAddress().getPort();

        aiApiService = new AiApiServiceImpl();
        aiApiUtil = newAiApiUtil("/qwen/chat");
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void chatShouldReuseSameUtilityForQwenAndDeepSeek() {
        List<AiChatMessage> messages = List.of(AiChatMessage.builder()
                .role("user")
                .content("hello")
                .build());

        AiApiChatResult qwenResult = aiApiUtil.chat(AiModelProvider.QWEN, messages,
                AiChatOptions.builder().temperature(0.7D).maxTokens(128).build());
        Assertions.assertEquals("qwen answer", qwenResult.answer());
        Assertions.assertEquals(9, qwenResult.totalTokens());
        Assertions.assertTrue(lastRequestBody.get().contains("\"model\":\"qwen-turbo\""));

        AiApiChatResult deepSeekResult = aiApiUtil.chat(AiModelProvider.DEEPSEEK, messages,
                AiChatOptions.builder().model("deepseek-chat").temperature(0.3D).maxTokens(256).build());
        Assertions.assertEquals("deepseek answer", deepSeekResult.answer());
        Assertions.assertEquals(11, deepSeekResult.totalTokens());
        Assertions.assertTrue(lastRequestBody.get().contains("\"model\":\"deepseek-chat\""));
    }

    @Test
    void structuredChatShouldParseJsonResponse() {
        AiApiUtil structuredUtil = newAiApiUtil("/structured/chat");
        IntentResult result = structuredUtil.structuredChat(AiModelProvider.QWEN,
                List.of(AiChatMessage.builder().role("user").content("login failed").build()),
                AiChatOptions.builder().temperature(0.1D).maxInputTokens(100).build(),
                IntentResult.class);

        Assertions.assertEquals("login", result.intent());
        Assertions.assertEquals(0.91D, result.confidence());
        Assertions.assertTrue(lastRequestBody.get().contains("\"response_format\":{\"type\":\"json_object\"}"));
    }

    @Test
    void streamChatShouldCollectChunksAndUsage() {
        AiApiUtil streamUtil = newAiApiUtil("/stream/chat");
        List<String> chunks = new ArrayList<>();
        AiApiChatResult result = streamUtil.streamChat(AiModelProvider.QWEN,
                List.of(AiChatMessage.builder().role("user").content("stream").build()),
                AiChatOptions.builder().temperature(0.7D).maxTokens(64).build(),
                chunks::add);

        Assertions.assertEquals(List.of("A", "I"), chunks);
        Assertions.assertEquals("AI", result.answer());
        Assertions.assertEquals(7, result.totalTokens());
        Assertions.assertTrue(lastRequestBody.get().contains("\"stream\":true"));
    }

    @Test
    void tokenEstimationShouldProtectInputLimit() {
        List<AiChatMessage> messages = List.of(AiChatMessage.builder()
                .role("user")
                .content("你好 SpringBoot")
                .build());

        int estimated = aiApiService.estimateMessagesTokens(messages);
        Assertions.assertTrue(estimated > 0);
        Assertions.assertDoesNotThrow(() -> aiApiService.validateInputTokens(messages, estimated));
        Assertions.assertThrows(AiApiException.class, () -> aiApiService.validateInputTokens(messages, 1));
    }

    private String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void writeJson(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private AiApiUtil newAiApiUtil(String qwenChatPath) {
        AiModelsProperties properties = new AiModelsProperties();
        Map<String, AiModelsProperties.Model> providers = new LinkedHashMap<>();
        providers.put("qwen", model(AiModelProvider.QWEN, "test-qwen-key", qwenChatPath, "qwen-turbo"));
        providers.put("deepseek", model(AiModelProvider.DEEPSEEK, "test-deepseek-key",
                "/deepseek/chat", "deepseek-chat"));
        properties.setProviders(providers);

        AiModelConfigRegistry registry = new AiModelConfigRegistry(properties, mock(RedisUtil.class));
        registry.refresh(properties);
        return new AiApiUtil(registry, new ObjectMapper(), aiApiService);
    }

    private AiModelsProperties.Model model(AiModelProvider provider, String apiKey, String chatPath,
                                           String defaultModel) {
        AiModelsProperties.Model model = new AiModelsProperties.Model();
        model.setProvider(provider);
        model.setApiKey(apiKey);
        model.setBaseUrl(baseUrl);
        model.setChatPath(chatPath);
        model.setDefaultModel(defaultModel);
        return model;
    }

    private record IntentResult(String intent, Double confidence) {
    }
}
