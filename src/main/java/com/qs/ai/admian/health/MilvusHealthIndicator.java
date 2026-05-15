package com.qs.ai.admian.health;

import com.qs.ai.admian.config.MilvusProperties;
import com.qs.ai.admian.service.MilvusVectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for Milvus.
 */
@Component("milvus")
@RequiredArgsConstructor
public class MilvusHealthIndicator implements HealthIndicator {

    private final MilvusVectorService milvusVectorService;
    private final MilvusProperties milvusProperties;

    @Override
    public Health health() {
        try {
            boolean collectionExists = milvusVectorService.hasCollection();
            return Health.up()
                    .withDetail("uri", milvusProperties.getUri())
                    .withDetail("collection", milvusProperties.getCollectionName())
                    .withDetail("collectionExists", collectionExists)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("uri", milvusProperties.getUri())
                    .withDetail("collection", milvusProperties.getCollectionName())
                    .build();
        }
    }
}
