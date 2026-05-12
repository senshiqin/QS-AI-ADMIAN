package com.qs.ai.admian.controller.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sanitized runtime model configuration response.
 */
public record AiModelConfigResponse(
        long version,
        LocalDateTime refreshedAt,
        String defaultProvider,
        Boolean fallbackToDefault,
        Boolean autoRefresh,
        Long refreshIntervalMs,
        String externalFile,
        Map<String, String> modelPrefixRoutes,
        List<Provider> providers
) {

    public record Provider(
            String key,
            String provider,
            String displayName,
            Boolean enabled,
            List<String> aliases,
            Boolean apiKeyConfigured,
            String baseUrl,
            String chatPath,
            String defaultModel,
            Double temperature,
            Integer maxTokens,
            Integer maxInputTokens,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Embedding embedding,
            Ollama ollama
    ) {
    }

    public record Embedding(
            Boolean enabled,
            String path,
            String model,
            Integer dimensions,
            Integer batchSize
    ) {
    }

    public record Ollama(
            Integer numPredict,
            Integer numCtx,
            Integer numThread,
            String keepAlive
    ) {
    }
}
