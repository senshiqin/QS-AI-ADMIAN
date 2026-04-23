package com.qs.ai.admian.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility for login/authentication scenarios.
 */
public final class JwtUtil {

    private static final Duration EXPIRE_DURATION = Duration.ofHours(2);
    private static final String ISSUER = "qs-ai-admian";

    private JwtUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String generateToken(String userId, String username, String secretKey) {
        Map<String, Object> claims = new HashMap<>(2);
        claims.put("userId", userId);
        claims.put("username", username);
        return generateToken(claims, userId, secretKey);
    }

    public static String generateToken(Map<String, Object> claims, String subject, String secretKey) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + EXPIRE_DURATION.toMillis());
        SecretKey key = buildKey(secretKey);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(ISSUER)
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(key)
                .compact();
    }

    public static Claims parseToken(String token, String secretKey) {
        SecretKey key = buildKey(secretKey);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static boolean validateToken(String token, String secretKey) {
        try {
            Claims claims = parseToken(token, secretKey);
            return claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean isTokenExpired(String token, String secretKey) {
        try {
            Claims claims = parseToken(token, secretKey);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean validateToken(String token, String secretKey, String expectedUserId) {
        try {
            Claims claims = parseToken(token, secretKey);
            String userId = claims.get("userId", String.class);
            return expectedUserId != null
                    && expectedUserId.equals(userId)
                    && claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    private static SecretKey buildKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("JWT secret key must not be blank");
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
