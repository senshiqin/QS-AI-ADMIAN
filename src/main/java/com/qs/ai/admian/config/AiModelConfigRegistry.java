package com.qs.ai.admian.config;

import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.dto.AiModelProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for the active model configuration.
 */
@Component
@RequiredArgsConstructor
public class AiModelConfigRegistry {

    private static final String QWEN_KEY = "qwen";
    private static final String DEEPSEEK_KEY = "deepseek";
    private static final String LLAMA3_KEY = "llama3";

    private final AiModelsProperties initialProperties;
    private final AtomicReference<AiModelsProperties> currentProperties = new AtomicReference<>();
    private final AtomicLong version = new AtomicLong();
    private volatile LocalDateTime refreshedAt;

    @PostConstruct
    public void init() {
        refresh(initialProperties);
    }

    public synchronized AiModelConfigState refresh(AiModelsProperties properties) {
        // 原子发布完整的标准化配置快照，避免请求过程中读到半更新状态。
        AiModelsProperties normalized = normalize(properties == null ? new AiModelsProperties() : properties);
        currentProperties.set(normalized);
        refreshedAt = LocalDateTime.now();
        version.incrementAndGet();
        return currentState();
    }

    public AiModelConfigState currentState() {
        AiModelsProperties properties = current();
        return new AiModelConfigState(
                version.get(),
                refreshedAt,
                properties.getSelection(),
                properties.getProviders()
        );
    }

    public AiModelsProperties current() {
        AiModelsProperties properties = currentProperties.get();
        return properties == null ? normalize(new AiModelsProperties()) : properties;
    }

    public Optional<Map.Entry<String, AiModelsProperties.Model>> findByKeyOrAlias(String provider) {
        if (!StringUtils.hasText(provider)) {
            return Optional.empty();
        }
        String normalizedProvider = normalizeKey(provider);
        AiModelsProperties properties = current();
        Map<String, AiModelsProperties.Model> providers = properties.getProviders();
        if (providers.containsKey(normalizedProvider)) {
            return Optional.of(Map.entry(normalizedProvider, providers.get(normalizedProvider)));
        }

        return providers.entrySet().stream()
                .filter(entry -> matchesProvider(entry.getKey(), entry.getValue(), normalizedProvider))
                .findFirst();
    }

    public Optional<Map.Entry<String, AiModelsProperties.Model>> findByProvider(AiModelProvider provider) {
        if (provider == null) {
            return Optional.empty();
        }
        return current().getProviders().entrySet().stream()
                .filter(entry -> provider == entry.getValue().getProvider())
                .findFirst();
    }

    public Map.Entry<String, AiModelsProperties.Model> requireOpenAiCompatibleProvider(AiModelProvider provider) {
        return findByProvider(provider)
                .filter(entry -> isEnabled(entry.getValue()))
                .orElseThrow(() -> new ParamException("AI model provider is not enabled: " + provider));
    }

    public boolean isEnabled(AiModelsProperties.Model model) {
        return model != null && !Boolean.FALSE.equals(model.getEnabled());
    }

    public List<Map.Entry<String, AiModelsProperties.Model>> enabledProviders() {
        return current().getProviders().entrySet().stream()
                .filter(entry -> isEnabled(entry.getValue()))
                .toList();
    }

    public List<Map.Entry<String, AiModelsProperties.Model>> providersByLongestPrefix() {
        return current().getProviders().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, AiModelsProperties.Model> entry) ->
                        entry.getKey().length()).reversed())
                .toList();
    }

    private boolean matchesProvider(String key, AiModelsProperties.Model model, String requested) {
        if (normalizeKey(key).equals(requested)) {
            return true;
        }
        if (model.getProvider() != null && normalizeKey(model.getProvider().name()).equals(requested)) {
            return true;
        }
        return model.getAliases() != null
                && model.getAliases().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeKey)
                .anyMatch(requested::equals);
    }

    private AiModelsProperties normalize(AiModelsProperties source) {
        AiModelsProperties result = new AiModelsProperties();
        result.setSelection(normalizeSelection(source.getSelection()));
        result.setRefresh(normalizeRefresh(source.getRefresh()));

        // 统一标准化供应商 key，后续调用不再受 YAML 大小写影响。
        Map<String, AiModelsProperties.Model> providers = new LinkedHashMap<>();
        if (source.getProviders() != null) {
            source.getProviders().forEach((key, model) -> {
                if (StringUtils.hasText(key)) {
                    providers.put(normalizeKey(key), normalizeModel(normalizeKey(key), model));
                }
            });
        }
        ensureDefaultProviders(providers);
        result.setProviders(providers);
        return result;
    }

    private AiModelsProperties.Selection normalizeSelection(AiModelsProperties.Selection source) {
        AiModelsProperties.Selection result = new AiModelsProperties.Selection();
        if (source != null) {
            result.setDefaultProvider(StringUtils.hasText(source.getDefaultProvider())
                    ? normalizeKey(source.getDefaultProvider())
                    : QWEN_KEY);
            result.setFallbackToDefault(source.getFallbackToDefault() == null
                    || source.getFallbackToDefault());
            if (source.getModelPrefixRoutes() != null) {
                source.getModelPrefixRoutes().forEach((prefix, provider) -> {
                    if (StringUtils.hasText(prefix) && StringUtils.hasText(provider)) {
                        result.getModelPrefixRoutes().put(normalizeKey(prefix), normalizeKey(provider));
                    }
                });
            }
        }
        if (result.getModelPrefixRoutes().isEmpty()) {
            // 即使 ai.models 为空，也保留基础的模型名前缀自动路由能力。
            result.getModelPrefixRoutes().put("qwen", QWEN_KEY);
            result.getModelPrefixRoutes().put("deepseek", DEEPSEEK_KEY);
            result.getModelPrefixRoutes().put("llama", LLAMA3_KEY);
            result.getModelPrefixRoutes().put("llama3", LLAMA3_KEY);
        }
        return result;
    }

    private AiModelsProperties.Refresh normalizeRefresh(AiModelsProperties.Refresh source) {
        AiModelsProperties.Refresh result = new AiModelsProperties.Refresh();
        if (source != null) {
            result.setAuto(Boolean.TRUE.equals(source.getAuto()));
            result.setIntervalMs(source.getIntervalMs() == null || source.getIntervalMs() <= 0
                    ? 60000L
                    : source.getIntervalMs());
            result.setExternalFile(source.getExternalFile());
        }
        return result;
    }

    private void ensureDefaultProviders(Map<String, AiModelsProperties.Model> providers) {
        // 补齐默认供应商配置骨架；密钥等敏感信息仍必须来自实际配置。
        providers.computeIfAbsent(QWEN_KEY, key -> normalizeModel(key, null));
        providers.computeIfAbsent(DEEPSEEK_KEY, key -> normalizeModel(key, null));
        providers.computeIfAbsent(LLAMA3_KEY, key -> normalizeModel(key, null));
    }

    private AiModelsProperties.Model normalizeModel(String key, AiModelsProperties.Model source) {
        AiModelsProperties.Model result = new AiModelsProperties.Model();
        if (source != null) {
            result.setEnabled(source.getEnabled() == null || source.getEnabled());
            result.setProvider(source.getProvider());
            result.setDisplayName(source.getDisplayName());
            result.setAliases(source.getAliases() == null ? new ArrayList<>() : new ArrayList<>(source.getAliases()));
            result.setApiKey(source.getApiKey());
            result.setBaseUrl(source.getBaseUrl());
            result.setChatPath(source.getChatPath());
            result.setDefaultModel(source.getDefaultModel());
            result.setTemperature(source.getTemperature());
            result.setMaxTokens(source.getMaxTokens());
            result.setMaxInputTokens(source.getMaxInputTokens());
            result.setConnectTimeoutMs(source.getConnectTimeoutMs());
            result.setReadTimeoutMs(source.getReadTimeoutMs());
            result.setEmbedding(source.getEmbedding());
            result.setOllama(source.getOllama());
        }

        AiModelProvider provider = result.getProvider() == null ? inferProvider(key) : result.getProvider();
        result.setProvider(provider);
        applyProviderDefaults(key, result, provider);
        addDefaultAliases(key, result);
        return result;
    }

    private AiModelProvider inferProvider(String key) {
        return switch (normalizeKey(key)) {
            case DEEPSEEK_KEY -> AiModelProvider.DEEPSEEK;
            case LLAMA3_KEY, "llama", "ollama", "local" -> AiModelProvider.OLLAMA;
            default -> AiModelProvider.QWEN;
        };
    }

    private void applyProviderDefaults(String key, AiModelsProperties.Model model, AiModelProvider provider) {
        // 云端 API 和本地 Ollama 的默认限制不同，这里按供应商分别兜底。
        switch (provider) {
            case DEEPSEEK -> applyDeepSeekDefaults(model);
            case OLLAMA -> applyOllamaDefaults(model);
            case QWEN -> applyQwenDefaults(model);
        }
        if (!StringUtils.hasText(model.getDisplayName())) {
            model.setDisplayName(key);
        }
        if (model.getConnectTimeoutMs() == null || model.getConnectTimeoutMs() <= 0) {
            model.setConnectTimeoutMs(5000);
        }
        if (model.getReadTimeoutMs() == null || model.getReadTimeoutMs() <= 0) {
            model.setReadTimeoutMs(provider == AiModelProvider.OLLAMA ? 120000 : 60000);
        }
        if (model.getTemperature() == null) {
            model.setTemperature(provider == AiModelProvider.OLLAMA ? 0.2D : 0.7D);
        }
        if (model.getMaxTokens() == null || model.getMaxTokens() <= 0) {
            model.setMaxTokens(provider == AiModelProvider.OLLAMA ? 1024 : 1200);
        }
        if (model.getMaxInputTokens() == null || model.getMaxInputTokens() <= 0) {
            model.setMaxInputTokens(provider == AiModelProvider.OLLAMA ? 4096 : 6000);
        }
    }

    private void applyQwenDefaults(AiModelsProperties.Model model) {
        if (!StringUtils.hasText(model.getBaseUrl())) {
            model.setBaseUrl("https://dashscope.aliyuncs.com");
        }
        if (!StringUtils.hasText(model.getChatPath())) {
            model.setChatPath("/compatible-mode/v1/chat/completions");
        }
        if (!StringUtils.hasText(model.getDefaultModel())) {
            model.setDefaultModel("qwen-turbo");
        }
        AiModelsProperties.Embedding embedding = model.getEmbedding() == null
                ? new AiModelsProperties.Embedding()
                : model.getEmbedding();
        embedding.setEnabled(embedding.getEnabled() == null || embedding.getEnabled());
        if (!StringUtils.hasText(embedding.getPath())) {
            embedding.setPath("/compatible-mode/v1/embeddings");
        }
        if (!StringUtils.hasText(embedding.getModel())) {
            embedding.setModel("text-embedding-v4");
        }
        if (embedding.getBatchSize() == null || embedding.getBatchSize() <= 0) {
            embedding.setBatchSize(10);
        }
        if (embedding.getDimensions() == null || embedding.getDimensions() <= 0) {
            embedding.setDimensions(1024);
        }
        model.setEmbedding(embedding);
    }

    private void applyDeepSeekDefaults(AiModelsProperties.Model model) {
        if (!StringUtils.hasText(model.getBaseUrl())) {
            model.setBaseUrl("https://api.deepseek.com");
        }
        if (!StringUtils.hasText(model.getChatPath())) {
            model.setChatPath("/chat/completions");
        }
        if (!StringUtils.hasText(model.getDefaultModel())) {
            model.setDefaultModel("deepseek-chat");
        }
    }

    private void applyOllamaDefaults(AiModelsProperties.Model model) {
        if (!StringUtils.hasText(model.getBaseUrl())) {
            model.setBaseUrl("http://localhost:11434");
        }
        if (!StringUtils.hasText(model.getChatPath())) {
            model.setChatPath("/api/chat");
        }
        if (!StringUtils.hasText(model.getDefaultModel())) {
            model.setDefaultModel("llama3.2:3b");
        }
        AiModelsProperties.Ollama ollama = model.getOllama() == null
                ? new AiModelsProperties.Ollama()
                : model.getOllama();
        if (ollama.getNumPredict() == null || ollama.getNumPredict() <= 0) {
            ollama.setNumPredict(model.getMaxTokens() == null ? 1024 : model.getMaxTokens());
        }
        if (ollama.getNumCtx() == null || ollama.getNumCtx() <= 0) {
            ollama.setNumCtx(4096);
        }
        if (!StringUtils.hasText(ollama.getKeepAlive())) {
            ollama.setKeepAlive("10m");
        }
        model.setOllama(ollama);
    }

    private void addDefaultAliases(String key, AiModelsProperties.Model model) {
        List<String> aliases = model.getAliases() == null ? new ArrayList<>() : model.getAliases();
        addAlias(aliases, key);
        addAlias(aliases, model.getProvider().name().toLowerCase(Locale.ROOT));
        if (model.getProvider() == AiModelProvider.QWEN) {
            addAlias(aliases, "dashscope");
            addAlias(aliases, "tongyi");
        } else if (model.getProvider() == AiModelProvider.OLLAMA) {
            addAlias(aliases, "llama");
            addAlias(aliases, "local");
        }
        model.setAliases(aliases);
    }

    private void addAlias(List<String> aliases, String value) {
        String alias = normalizeKey(value);
        if (StringUtils.hasText(alias) && !aliases.contains(alias)) {
            aliases.add(alias);
        }
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
