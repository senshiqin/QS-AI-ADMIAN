package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus vector database configuration.
 */
@Data
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private String uri = "http://localhost:19530";

    private String token;

    private String databaseName = "default";

    private String collectionName = "qs_ai_knowledge_chunks";

    private Integer dimension = 1024;

    private Boolean autoCreateCollection = true;

    private String metricType = "COSINE";

    private String indexType = "AUTOINDEX";

    private Integer contentMaxLength = 8192;

    private Float similarityThreshold = 0.7F;

    private Long connectTimeoutMs = 5000L;

    private Long rpcDeadlineMs = 60000L;
}
