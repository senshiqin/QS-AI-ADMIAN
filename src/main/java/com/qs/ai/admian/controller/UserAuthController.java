package com.qs.ai.admian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qs.ai.admian.controller.request.LoginRequest;
import com.qs.ai.admian.controller.response.LoginResponse;
import com.qs.ai.admian.entity.SysUser;
import com.qs.ai.admian.service.SysUserService;
import com.qs.ai.admian.util.JwtUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * User auth controller.
 */
@Tag(name = "Auth", description = "User login APIs")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserAuthController {

    private final SysUserService sysUserService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Operation(summary = "User login", description = "Validate username/password from sys_user and return JWT token.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            return ApiResponse.fail(ResultCode.UNAUTHORIZED.getCode(), "Username or password is incorrect");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return ApiResponse.fail(ResultCode.FORBIDDEN.getCode(), "User is disabled");
        }
        // For demo simplicity: compare plaintext. Replace with BCrypt/Argon2 verification in production.
        if (!request.getPassword().equals(user.getPasswordHash())) {
            return ApiResponse.fail(ResultCode.UNAUTHORIZED.getCode(), "Username or password is incorrect");
        }

        String userId = String.valueOf(user.getId());
        String token = JwtUtil.generateToken(userId, user.getUsername(), jwtSecret);
        LoginResponse response = LoginResponse.builder()
                .userId(userId)
                .username(user.getUsername())
                .tokenType("Bearer")
                .accessToken(token)
                .expiresInSeconds(7200L)
                .build();
        user.setLastLoginTime(LocalDateTime.now());
        sysUserService.updateById(user);
        return ApiResponse.success("Login success", response);
    }
}
