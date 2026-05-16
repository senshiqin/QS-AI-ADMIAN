package com.qs.ai.admian.config.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.config.JwtProperties;
import com.qs.ai.admian.util.JwtUtil;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT authentication interceptor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String TOKEN_HEADER = "token";

    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            writeUnauthorized(response, "User not logged in or token missing");
            return false;
        }

        Claims claims;
        try {
            claims = JwtUtil.parseToken(token, jwtProperties.getSecret());
        } catch (ExpiredJwtException ex) {
            writeForbidden(response, "Token expired, please login again");
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed, uri={}, message={}", request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response, "Invalid token");
            return false;
        }

        request.setAttribute("loginUserId", claims.get("userId", String.class));
        request.setAttribute("loginUsername", claims.get("username", String.class));
        log.debug("JWT auth success, uri={}, userId={}", request.getRequestURI(), claims.get("userId", String.class));
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(TOKEN_PREFIX)) {
            return authHeader.substring(TOKEN_PREFIX.length());
        }
        return request.getHeader(TOKEN_HEADER);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.fail(ResultCode.UNAUTHORIZED.getCode(), message));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, ApiResponse.fail(ResultCode.FORBIDDEN.getCode(), message));
    }

    private void writeJson(HttpServletResponse response, int httpStatus, ApiResponse<Void> body) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
