package com.qs.ai.admian.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lazily creates the Milvus client when vector APIs are actually used.
 */
@Component
@RequiredArgsConstructor
public class MilvusClientHolder {

    private final MilvusProperties properties;

    private volatile MilvusClientV2 client;

    public MilvusClientV2 getClient() {
        MilvusClientV2 current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = new MilvusClientV2(buildConnectConfig());
            }
            return client;
        }
    }

    @PreDestroy
    public void close() {
        MilvusClientV2 current = client;
        if (current != null) {
            current.close();
        }
    }

    private ConnectConfig buildConnectConfig() {
        ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                .uri(properties.getUri())
                .dbName(properties.getDatabaseName())
                .connectTimeoutMs(properties.getConnectTimeoutMs())
                .rpcDeadlineMs(properties.getRpcDeadlineMs());

        if (StringUtils.hasText(properties.getToken())) {
            builder.token(properties.getToken());
        }

        return builder.build();
    }
}
