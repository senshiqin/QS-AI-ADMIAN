package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Scheduled maintenance task configuration.
 */
@Data
@ConfigurationProperties(prefix = "maintenance")
public class MaintenanceProperties {

    private Cleanup cleanup = new Cleanup();

    private CacheWarmup cacheWarmup = new CacheWarmup();

    @Data
    public static class Cleanup {

        private Boolean enabled = true;

        private String tempDir = "uploads/temp";

        private Integer expiredHours = 24;

        private String cron = "0 0 3 * * ?";
    }

    @Data
    public static class CacheWarmup {

        private Boolean enabled = true;

        private String cron = "0 */10 * * * ?";
    }
}
