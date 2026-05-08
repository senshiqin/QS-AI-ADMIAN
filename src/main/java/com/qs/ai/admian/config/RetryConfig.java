package com.qs.ai.admian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Retry configuration for external AI API calls.
 */
@EnableRetry
@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate aiApiRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                TimeoutException.class, true,
                HttpTimeoutException.class, true,
                SocketTimeoutException.class, true,
                ConnectException.class, true,
                ResourceAccessException.class, true
        );
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3, retryableExceptions, true));

        return retryTemplate;
    }
}
