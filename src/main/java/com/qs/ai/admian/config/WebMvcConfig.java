package com.qs.ai.admian.config;

import com.qs.ai.admian.config.interceptor.JwtAuthInterceptor;
import com.qs.ai.admian.config.interceptor.AiRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC config for interceptor registration.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AiRateLimitInterceptor aiRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/ai/**", "/api/v1/user/**")
                .excludePathPatterns("/api/v1/user/login", "/api/v1/user/login/**")
                .order(0);
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns("/api/v1/ai/chat/**")
                .order(1);
    }
}
