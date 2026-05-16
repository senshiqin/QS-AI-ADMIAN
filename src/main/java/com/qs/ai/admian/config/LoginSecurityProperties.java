package com.qs.ai.admian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Login brute-force protection settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.login")
public class LoginSecurityProperties {

    private boolean failureLimitEnabled = true;
    private int maxFailures = 5;
    private long failureWindowSeconds = 300L;
    private long lockSeconds = 900L;
}
