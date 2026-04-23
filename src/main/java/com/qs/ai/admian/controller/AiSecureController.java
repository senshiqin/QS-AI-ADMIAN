package com.qs.ai.admian.controller;

import com.qs.ai.admian.util.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Secured AI APIs for JWT interceptor verification.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiSecureController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping(HttpServletRequest request) {
        return ApiResponse.success(Map.of(
                "message", "AI endpoint authorized",
                "userId", request.getAttribute("loginUserId"),
                "username", request.getAttribute("loginUsername")
        ));
    }
}
