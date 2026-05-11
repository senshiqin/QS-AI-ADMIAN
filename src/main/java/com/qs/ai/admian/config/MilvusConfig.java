package com.qs.ai.admian.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus client configuration.
 */
@Configuration
@EnableConfigurationProperties(MilvusProperties.class)
public class MilvusConfig {
}
