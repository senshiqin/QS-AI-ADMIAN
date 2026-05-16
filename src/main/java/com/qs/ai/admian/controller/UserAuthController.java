package com.qs.ai.admian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qs.ai.admian.config.JwtProperties;
import com.qs.ai.admian.config.LoginSecurityProperties;
import com.qs.ai.admian.controller.request.LoginRequest;
import com.qs.ai.admian.controller.response.LoginResponse;
import com.qs.ai.admian.entity.SysLoginLog;
import com.qs.ai.admian.entity.SysUser;
import com.qs.ai.admian.service.SysLoginLogService;
import com.qs.ai.admian.service.SysUserService;
import com.qs.ai.admian.util.JwtUtil;
import com.qs.ai.admian.util.RedisUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * User auth controller.
 */
@Tag(name = "Auth", description = "User login APIs")
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserAuthController {

    private static final String LOGIN_LOCK_KEY_PREFIX = "auth:login:lock:";
    private static final String LOGIN_FAILURE_KEY_PREFIX = "auth:login:fail:";
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final SysUserService sysUserService;
    private final SysLoginLogService sysLoginLogService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final LoginSecurityProperties loginSecurityProperties;
    private final RedisUtil redisUtil;

    @Operation(summary = "User login", description = "Validate username/password from sys_user and return JWT token.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest) {
        String username = request.getUsername().trim();
        String clientIp = resolveClientIp(servletRequest);
        if (isLoginLocked(username)) {
            recordLogin(username, null, clientIp, servletRequest, false, "LOGIN_LOCKED");
            return ApiResponse.fail(ResultCode.TOO_MANY_REQUESTS.getCode(),
                    "Too many failed login attempts, please try again later");
        }

        SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            recordFailedLogin(username, null, clientIp, servletRequest, "BAD_CREDENTIALS");
            return ApiResponse.fail(ResultCode.UNAUTHORIZED.getCode(), "Username or password is incorrect");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            recordLogin(username, user.getId(), clientIp, servletRequest, false, "USER_DISABLED");
            return ApiResponse.fail(ResultCode.FORBIDDEN.getCode(), "User is disabled");
        }
        if (!matchesPassword(request.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(username, user.getId(), clientIp, servletRequest, "BAD_CREDENTIALS");
            return ApiResponse.fail(ResultCode.UNAUTHORIZED.getCode(), "Username or password is incorrect");
        }

        String userId = String.valueOf(user.getId());
        String token = JwtUtil.generateToken(
                userId,
                user.getUsername(),
                jwtProperties.getSecret(),
                Duration.ofSeconds(jwtProperties.getAccessTokenExpireSeconds())
        );
        LoginResponse response = LoginResponse.builder()
                .userId(userId)
                .username(user.getUsername())
                .tokenType("Bearer")
                .accessToken(token)
                .expiresInSeconds(jwtProperties.getAccessTokenExpireSeconds())
                .build();
        upgradePlaintextPasswordIfNeeded(user, request.getPassword());
        user.setLastLoginTime(LocalDateTime.now());
        sysUserService.updateById(user);
        clearLoginFailures(username);
        recordLogin(username, user.getId(), clientIp, servletRequest, true, null);
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

    private boolean isLoginLocked(String username) {
        if (!loginSecurityProperties.isFailureLimitEnabled()) {
            return false;
        }
        try {
            return redisUtil.get(lockKey(username)) != null;
        } catch (RuntimeException ex) {
            log.warn("Login lock check skipped because Redis is unavailable, username={}", username, ex);
            return false;
        }
    }

    private void recordFailedLogin(String username,
                                   Long userId,
                                   String clientIp,
                                   HttpServletRequest servletRequest,
                                   String reason) {
        recordLogin(username, userId, clientIp, servletRequest, false, reason);
        if (!loginSecurityProperties.isFailureLimitEnabled()) {
            return;
        }
        try {
            String failureKey = failureKey(username);
            Long failures = redisUtil.increment(failureKey);
            if (failures != null && failures == 1L) {
                redisUtil.expire(failureKey, loginSecurityProperties.getFailureWindowSeconds(), TimeUnit.SECONDS);
            }
            if (failures != null && failures >= loginSecurityProperties.getMaxFailures()) {
                redisUtil.set(lockKey(username), "1", loginSecurityProperties.getLockSeconds(), TimeUnit.SECONDS);
                redisUtil.delete(failureKey);
                log.warn("Login locked after repeated failures, username={}, failures={}, lockSeconds={}",
                        username, failures, loginSecurityProperties.getLockSeconds());
            }
        } catch (RuntimeException ex) {
            log.warn("Login failure counter skipped because Redis is unavailable, username={}", username, ex);
        }
    }

    private void clearLoginFailures(String username) {
        if (!loginSecurityProperties.isFailureLimitEnabled()) {
            return;
        }
        try {
            redisUtil.delete(failureKey(username));
            redisUtil.delete(lockKey(username));
        } catch (RuntimeException ex) {
            log.warn("Login failure counter cleanup skipped because Redis is unavailable, username={}", username, ex);
        }
    }

    private void recordLogin(String username,
                             Long userId,
                             String clientIp,
                             HttpServletRequest servletRequest,
                             boolean success,
                             String failureReason) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUsername(username);
        loginLog.setUserId(userId);
        loginLog.setClientIp(clientIp);
        loginLog.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
        loginLog.setSuccess(success ? 1 : 0);
        loginLog.setFailureReason(failureReason);
        loginLog.setTraceId(MDC.get("traceId"));
        loginLog.setLoginTime(LocalDateTime.now());
        try {
            sysLoginLogService.save(loginLog);
        } catch (RuntimeException ex) {
            log.warn("Failed to save login audit log, username={}, success={}, reason={}",
                    username, success, failureReason, ex);
        }
    }

    private String failureKey(String username) {
        return LOGIN_FAILURE_KEY_PREFIX + normalizeUsername(username);
    }

    private String lockKey(String username) {
        return LOGIN_LOCK_KEY_PREFIX + normalizeUsername(username);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
