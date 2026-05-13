package com.qs.ai.admian.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger OpenAPI configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .info(new Info()
                        .title("QS-AI Backend API")
                        .description("""
                                Spring Boot 3.2.5 / JDK 17 AI backend interface documentation.

                                Main modules:
                                - Auth: JWT login.
                                - AI Chat: multi-model chat and SSE streaming.
                                - AI RAG: retrieval, streaming answer and async ingestion.
                                - AI Documents: knowledge document query, delete and batch upload.
                                - AI Model Config: runtime model configuration and refresh.

                                All protected /api/v1/ai/** APIs require Bearer JWT authorization.
                                Responses use ApiResponse with code, message, data, traceId and timestamp.
                                """)
                        .version("v1.0.0")
                        .contact(new Contact().name("QS-AI Team")));
    }
}
