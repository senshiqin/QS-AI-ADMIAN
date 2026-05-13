package com.qs.ai.admian.controller;

import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.AiModelConfigState;
import com.qs.ai.admian.config.AiModelsProperties;
import com.qs.ai.admian.controller.response.AiModelConfigResponse;
import com.qs.ai.admian.service.AiModelConfigRefreshService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Runtime AI model configuration APIs.
 */
@Tag(name = "AI Model Config", description = "Runtime model configuration and refresh APIs")
@RestController
@RequestMapping("/api/v1/ai/model-config")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigRegistry registry;
    private final AiModelConfigRefreshService refreshService;

    @Operation(summary = "Get active AI model configuration")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<AiModelConfigResponse> current() {
        return ApiResponse.success(toResponse(registry.cachedCurrentState()));
    }

    @Operation(summary = "Refresh AI model configuration without restarting")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/refresh")
    public ApiResponse<AiModelConfigResponse> refresh() {
        return ApiResponse.success("AI model configuration refreshed", toResponse(refreshService.refresh()));
    }

    private AiModelConfigResponse toResponse(AiModelConfigState state) {
        AiModelsProperties.Refresh refresh = registry.current().getRefresh();
        return new AiModelConfigResponse(
                state.version(),
                state.refreshedAt(),
                state.selection().getDefaultProvider(),
                state.selection().getFallbackToDefault(),
                refresh.getAuto(),
                refresh.getIntervalMs(),
                refresh.getExternalFile(),
                state.selection().getModelPrefixRoutes(),
                state.providers().entrySet().stream()
                        .map(this::toProviderResponse)
                        .toList()
        );
    }

    private AiModelConfigResponse.Provider toProviderResponse(Map.Entry<String, AiModelsProperties.Model> entry) {
        AiModelsProperties.Model model = entry.getValue();
        AiModelsProperties.Embedding embedding = model.getEmbedding();
        AiModelsProperties.Ollama ollama = model.getOllama();
        return new AiModelConfigResponse.Provider(
                entry.getKey(),
                model.getProvider() == null ? null : model.getProvider().name(),
                model.getDisplayName(),
                model.getEnabled(),
                model.getAliases(),
                StringUtils.hasText(model.getApiKey()),
                model.getBaseUrl(),
                model.getChatPath(),
                model.getDefaultModel(),
                model.getTemperature(),
                model.getMaxTokens(),
                model.getMaxInputTokens(),
                model.getConnectTimeoutMs(),
                model.getReadTimeoutMs(),
                embedding == null ? null : new AiModelConfigResponse.Embedding(
                        embedding.getEnabled(),
                        embedding.getPath(),
                        embedding.getModel(),
                        embedding.getDimensions(),
                        embedding.getBatchSize()
                ),
                ollama == null ? null : new AiModelConfigResponse.Ollama(
                        ollama.getNumPredict(),
                        ollama.getNumCtx(),
                        ollama.getNumThread(),
                        ollama.getKeepAlive()
                )
        );
    }
}
