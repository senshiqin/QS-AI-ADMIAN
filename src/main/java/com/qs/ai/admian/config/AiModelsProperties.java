package com.qs.ai.admian.config;

import com.qs.ai.admian.service.dto.AiModelProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime AI model configuration.
 */
@Data
@ConfigurationProperties(prefix = "ai.models")
public class AiModelsProperties {

    private Selection selection = new Selection();

    private Refresh refresh = new Refresh();

    private Map<String, Model> providers = new LinkedHashMap<>();

    @Data
    public static class Selection {

        private String defaultProvider = "qwen";

        private Boolean fallbackToDefault = true;

        private Map<String, String> modelPrefixRoutes = new LinkedHashMap<>();
    }

    @Data
    public static class Refresh {

        private Boolean auto = false;

        private Long intervalMs = 60000L;

        private String externalFile;
    }

    @Data
    public static class Model {

        private Boolean enabled = true;

        private AiModelProvider provider;

        private String displayName;

        private List<String> aliases = new ArrayList<>();

        private String apiKey;

        private String baseUrl;

        private String chatPath;

        private String defaultModel;

        private Double temperature;

        private Integer maxTokens;

        private Integer maxInputTokens;

        private Integer connectTimeoutMs;

        private Integer readTimeoutMs;

        private Embedding embedding = new Embedding();

        private Ollama ollama = new Ollama();
    }

    @Data
    public static class Embedding {

        private Boolean enabled = false;

        private String path;

        private String model;

        private Integer dimensions;

        private Integer batchSize;
    }

    @Data
    public static class Ollama {

        private Integer numPredict;

        private Integer numCtx;

        private Integer numThread;

        private String keepAlive;
    }
}
