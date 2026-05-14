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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

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
        if (!matchesPassword(request.getPassword(), user.getPasswordHash())) {
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
        upgradePlaintextPasswordIfNeeded(user, request.getPassword());
        user.setLastLoginTime(LocalDateTime.now());
        sysUserService.updateById(user);
        return ApiResponse.success("Login success", response);
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        // 兼容历史明文密码，登录成功后会自动升级为 BCrypt。
        return rawPassword.equals(storedPassword);
    }

    private void upgradePlaintextPasswordIfNeeded(SysUser user, String rawPassword) {
        if (!isBcryptHash(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
    }

    private boolean isBcryptHash(String password) {
        return password != null
                && (password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$"));
    }
}
