package com.qs.ai.admian.service;

import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.AiModelConfigState;
import com.qs.ai.admian.config.AiModelsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reloads model configuration from application YAML files without restarting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigRefreshService {

    private static final String CONFIG_PREFIX = "ai.models";

    private final AiModelConfigRegistry registry;
    private final ConfigurableEnvironment environment;
    private final YamlPropertySourceLoader yamlPropertySourceLoader = new YamlPropertySourceLoader();

    public AiModelConfigState refresh() {
        AiModelsProperties properties = loadProperties();
        AiModelConfigState state = registry.refresh(properties);
        log.info("AI model configuration refreshed, version={}, providers={}",
                state.version(), state.providers().keySet());
        return state;
    }

    @Scheduled(fixedDelayString = "${ai.models.refresh.interval-ms:60000}")
    public void autoRefresh() {
        if (!Boolean.TRUE.equals(registry.current().getRefresh().getAuto())) {
            return;
        }
        try {
            refresh();
        } catch (Exception ex) {
            log.warn("AI model configuration auto refresh failed", ex);
        }
    }

    private AiModelsProperties loadProperties() {
        List<PropertySource<?>> propertySources = loadModelPropertySources();
        if (propertySources.isEmpty()) {
            return Binder.get(environment)
                    .bind(CONFIG_PREFIX, Bindable.of(AiModelsProperties.class))
                    .orElseGet(AiModelsProperties::new);
        }

        // 将可重载 YAML 放到 Environment 前面，确保 /refresh 后文件修改能覆盖启动时配置。
        MutablePropertySources mutablePropertySources = new MutablePropertySources();
        propertySources.forEach(mutablePropertySources::addLast);
        environment.getPropertySources().forEach(mutablePropertySources::addLast);

        Binder binder = new Binder(
                ConfigurationPropertySources.from(mutablePropertySources),
                new PropertySourcesPlaceholdersResolver(mutablePropertySources)
        );
        return binder.bind(CONFIG_PREFIX, Bindable.of(AiModelsProperties.class))
                .orElseGet(AiModelsProperties::new);
    }

    private List<PropertySource<?>> loadModelPropertySources() {
        List<Resource> lowToHighPrecedenceResources = candidateResources();
        List<PropertySource<?>> lowToHighPrecedenceSources = new ArrayList<>();
        int index = 0;
        for (Resource resource : lowToHighPrecedenceResources) {
            if (!resource.exists()) {
                continue;
            }
            try {
                lowToHighPrecedenceSources.addAll(yamlPropertySourceLoader.load(
                        "ai-model-config-" + index++,
                        resource
                ));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load AI model config resource: " + resource, ex);
            }
        }
        // Binder 优先读取靠前的配置源，反转后可保证 profile/外部文件覆盖基础配置。
        Collections.reverse(lowToHighPrecedenceSources);
        return lowToHighPrecedenceSources;
    }

    private List<Resource> candidateResources() {
        List<Resource> resources = new ArrayList<>();
        addClasspathApplicationResources(resources);
        addFileApplicationResources(resources, Path.of("src", "main", "resources"));
        addFileApplicationResources(resources, Path.of("").toAbsolutePath());
        addFileApplicationResources(resources, Path.of("config").toAbsolutePath());

        String externalFile = environment.getProperty(CONFIG_PREFIX + ".refresh.external-file");
        if (!StringUtils.hasText(externalFile)) {
            externalFile = registry.current().getRefresh().getExternalFile();
        }
        // 外部配置文件最后加入，反转后拥有最高优先级。
        if (StringUtils.hasText(externalFile)) {
            resources.add(new FileSystemResource(Path.of(externalFile.trim()).toAbsolutePath()));
        }
        return resources;
    }

    private void addClasspathApplicationResources(List<Resource> resources) {
        resources.add(new ClassPathResource("application.yml"));
        resources.add(new ClassPathResource("application.yaml"));
        for (String profile : environment.getActiveProfiles()) {
            resources.add(new ClassPathResource("application-" + profile + ".yml"));
            resources.add(new ClassPathResource("application-" + profile + ".yaml"));
        }
    }

    private void addFileApplicationResources(List<Resource> resources, Path directory) {
        resources.add(new FileSystemResource(directory.resolve("application.yml").toAbsolutePath()));
        resources.add(new FileSystemResource(directory.resolve("application.yaml").toAbsolutePath()));
        for (String profile : environment.getActiveProfiles()) {
            resources.add(new FileSystemResource(directory.resolve("application-" + profile + ".yml").toAbsolutePath()));
            resources.add(new FileSystemResource(directory.resolve("application-" + profile + ".yaml").toAbsolutePath()));
        }
    }
}
