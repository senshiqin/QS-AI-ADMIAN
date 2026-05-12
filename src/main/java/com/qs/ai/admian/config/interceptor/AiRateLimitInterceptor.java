package com.qs.ai.admian.config.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.util.RedisUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Redis fixed-window rate limiter for AI chat APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String RATE_LIMIT_KEY_PREFIX = "ai:rate:chat:";
    private static final long WINDOW_EXPIRE_SECONDS = 70L;
    private static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    @Value("${ai.rate-limit.chat-per-minute:10}")
    private long chatLimitPerMinute;

    @Value("${ai.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        String userId = String.valueOf(request.getAttribute("loginUserId"));
        if (userId == null || userId.isBlank() || "null".equals(userId)) {
            writeTooManyRequests(response, "User identity missing, please login again");
            return false;
        }

        String key = buildRateLimitKey(userId);
        Long count = redisUtil.increment(key);
        if (count != null && count == 1L) {
            redisUtil.expire(key, WINDOW_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }

        if (count != null && count > chatLimitPerMinute) {
            log.warn("AI chat rate limited, userId={}, key={}, count={}, limit={}",
                    userId, key, count, chatLimitPerMinute);
            response.setHeader("Retry-After", String.valueOf(secondsUntilNextMinute()));
            writeTooManyRequests(response, "AI chat rate limit exceeded, max 10 requests per minute");
            return false;
        }
        return true;
    }

    private String buildRateLimitKey(String userId) {
        return RATE_LIMIT_KEY_PREFIX + userId + ":" + LocalDateTime.now().format(MINUTE_FORMATTER);
    }

    private long secondsUntilNextMinute() {
        return 60L - LocalDateTime.now().getSecond();
    }

    private void writeTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HTTP_STATUS_TOO_MANY_REQUESTS);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ResultCode.TOO_MANY_REQUESTS.getCode(), message)
        ));
    }
}
