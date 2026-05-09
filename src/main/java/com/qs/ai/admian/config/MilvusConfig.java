package com.qs.ai.admian.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Milvus client configuration.
 */
@Configuration
@EnableConfigurationProperties(MilvusProperties.class)
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(MilvusProperties properties) {
        ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                .uri(properties.getUri())
                .dbName(properties.getDatabaseName())
                .connectTimeoutMs(properties.getConnectTimeoutMs())
                .rpcDeadlineMs(properties.getRpcDeadlineMs());

        if (StringUtils.hasText(properties.getToken())) {
            builder.token(properties.getToken());
        }

        return new MilvusClientV2(builder.build());
    }
}
