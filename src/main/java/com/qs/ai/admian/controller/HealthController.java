package com.qs.ai.admian.controller;

import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查控制器。
 */
@Tag(name = "Health", description = "Application health check APIs")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "Health check", description = "Return UP when the application process is alive.")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
