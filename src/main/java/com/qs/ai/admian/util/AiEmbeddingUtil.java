package com.qs.ai.admian.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.AiModelsProperties;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.AiApiService;
import com.qs.ai.admian.service.dto.AiModelProvider;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope embedding utility for RAG indexing and retrieval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiEmbeddingUtil {

    private static final String PROVIDER_NAME = "DashScope Embedding";

    private final AiModelConfigRegistry modelConfigRegistry;
    private final ObjectMapper objectMapper;
    private final AiApiService aiApiService;

    public float[] embed(String text) {
        List<float[]> embeddings = embedBatch(List.of(text));
        return embeddings.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        long startTime = System.currentTimeMillis();
        List<String> cleanTexts = validateAndCleanTexts(texts);
        AiModelsProperties.Model modelConfig = resolveEmbeddingModelConfig();
        aiApiService.validateApiKey(modelConfig.getApiKey(), PROVIDER_NAME);

        int batchSize = resolveBatchSize(modelConfig);
        List<float[]> embeddings = new ArrayList<>(cleanTexts.size());
        int batchCount = 0;
        try {
            for (int start = 0; start < cleanTexts.size(); start += batchSize) {
                int end = Math.min(start + batchSize, cleanTexts.size());
                batchCount++;
                embeddings.addAll(callEmbeddingApi(modelConfig, cleanTexts.subList(start, end)));
            }
            log.info("Embedding batch completed, model={}, textCount={}, batchSize={}, batchCount={}, durationMs={}",
                    modelConfig.getEmbedding().getModel(), cleanTexts.size(), batchSize, batchCount,
                    System.currentTimeMillis() - startTime);
            return embeddings;
        } catch (RuntimeException ex) {
            log.warn("Embedding batch failed, model={}, textCount={}, batchSize={}, batchCount={}, durationMs={}",
                    modelConfig.getEmbedding().getModel(), cleanTexts.size(), batchSize, batchCount,
                    System.currentTimeMillis() - startTime, ex);
            throw ex;
        }
    }

    public Embedding embedAsEmbedding(String text) {
        return Embedding.from(embed(text));
    }

    public List<Embedding> embedBatchAsEmbeddings(List<String> texts) {
        return embedBatch(texts).stream()
                .map(Embedding::from)
                .toList();
    }

    public String getEmbeddingModel() {
        return resolveEmbeddingModelConfig().getEmbedding().getModel();
    }

    public int getEmbeddingDimension() {
        Integer dimensions = resolveEmbeddingModelConfig().getEmbedding().getDimensions();
        return dimensions == null || dimensions <= 0 ? 1024 : dimensions;
    }

    private List<float[]> callEmbeddingApi(AiModelsProperties.Model modelConfig, List<String> texts) {
        long startTime = System.currentTimeMillis();
        AiModelsProperties.Embedding embedding = modelConfig.getEmbedding();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", embedding.getModel());
        requestBody.put("input", texts);
        requestBody.put("encoding_format", "float");
        Integer dimensions = embedding.getDimensions();
        if (dimensions != null && dimensions > 0) {
            requestBody.put("dimensions", dimensions);
        }

        try {
            String responseBody = aiApiService.executeWithRetry(
                    PROVIDER_NAME + " API request",
                    () -> buildRestClient(modelConfig).post()
                            .uri(embedding.getPath())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + modelConfig.getApiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .body(String.class)
            );
            List<float[]> result = parseEmbeddingResponse(responseBody, texts.size());
            log.info("Embedding API call completed, model={}, textCount={}, durationMs={}",
                    embedding.getModel(), texts.size(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception ex) {
            log.warn("Embedding API call failed, model={}, textCount={}, durationMs={}",
                    embedding.getModel(), texts.size(), System.currentTimeMillis() - startTime, ex);
            throw aiApiService.toAiApiException(PROVIDER_NAME + " API request", ex);
        }
    }

    private List<float[]> parseEmbeddingResponse(String responseBody, int expectedSize) {
        if (!StringUtils.hasText(responseBody)) {
            throw new AiApiException("Embedding API returned empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                throw new AiApiException("Embedding API response does not contain embedding data");
            }
            if (dataNode.size() != expectedSize) {
                throw new AiApiException("Embedding API response size mismatch, expected="
                        + expectedSize + ", actual=" + dataNode.size());
            }

            List<EmbeddingItem> items = new ArrayList<>(dataNode.size());
            for (JsonNode itemNode : dataNode) {
                int index = itemNode.path("index").asInt(items.size());
                JsonNode embeddingNode = itemNode.path("embedding");
                if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                    throw new AiApiException("Embedding API response contains empty embedding vector");
                }
                items.add(new EmbeddingItem(index, toFloatArray(embeddingNode)));
            }

            return items.stream()
                    .sorted(Comparator.comparingInt(EmbeddingItem::index))
                    .map(EmbeddingItem::vector)
                    .toList();
        } catch (AiApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse embedding response, body={}", responseBody, ex);
            throw new AiApiException("Failed to parse embedding API response");
        }
    }

    private float[] toFloatArray(JsonNode embeddingNode) {
        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    private List<String> validateAndCleanTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new ParamException("texts must not be empty");
        }

        List<String> cleanTexts = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String cleanText = TextParseUtil.cleanText(texts.get(i));
            if (!StringUtils.hasText(cleanText)) {
                throw new ParamException("texts[" + i + "] must not be blank");
            }
            cleanTexts.add(cleanText);
        }
        return cleanTexts;
    }

    private int resolveBatchSize(AiModelsProperties.Model modelConfig) {
        Integer configuredBatchSize = modelConfig.getEmbedding().getBatchSize();
        if (configuredBatchSize == null || configuredBatchSize <= 0) {
            return 10;
        }
        return Math.min(configuredBatchSize, 10);
    }

    private RestClient buildRestClient(AiModelsProperties.Model modelConfig) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(modelConfig.getConnectTimeoutMs());
        requestFactory.setReadTimeout(modelConfig.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(modelConfig.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private AiModelsProperties.Model resolveEmbeddingModelConfig() {
        return modelConfigRegistry.findByProvider(AiModelProvider.QWEN)
                .filter(entry -> modelConfigRegistry.isEnabled(entry.getValue()))
                .map(Map.Entry::getValue)
                .filter(model -> model.getEmbedding() != null && !Boolean.FALSE.equals(model.getEmbedding().getEnabled()))
                .orElseThrow(() -> new AiApiException(PROVIDER_NAME + " provider is not enabled"));
    }

    private record EmbeddingItem(int index, float[] vector) {
    }
}
