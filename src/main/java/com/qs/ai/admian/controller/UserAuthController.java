package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.LoginRequest;
import com.qs.ai.admian.controller.response.LoginResponse;
import com.qs.ai.admian.util.JwtUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User auth controller.
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserAuthController {

    private static final String DEMO_USERNAME = "admin";
    private static final String DEMO_PASSWORD = "123456";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        if (!DEMO_USERNAME.equals(request.getUsername()) || !DEMO_PASSWORD.equals(request.getPassword())) {
            return ApiResponse.fail(ResultCode.UNAUTHORIZED.getCode(), "Username or password is incorrect");
        }

        String userId = "u1001";
        String token = JwtUtil.generateToken(userId, request.getUsername(), jwtSecret);
        LoginResponse response = LoginResponse.builder()
                .userId(userId)
                .username(request.getUsername())
                .tokenType("Bearer")
                .accessToken(token)
                .expiresInSeconds(7200L)
                .build();
        return ApiResponse.success("Login success", response);
    }
}
