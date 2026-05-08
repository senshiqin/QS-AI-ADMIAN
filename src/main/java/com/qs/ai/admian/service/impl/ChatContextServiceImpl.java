package com.qs.ai.admian.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.service.ChatContextService;
import com.qs.ai.admian.service.dto.ChatContextMessage;
import com.qs.ai.admian.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed chat context service for AI context management.
 */
@Service
@RequiredArgsConstructor
public class ChatContextServiceImpl implements ChatContextService {

    private static final long CONTEXT_EXPIRE_MINUTES = 60L;
    private static final long MAX_CONTEXT_MESSAGES = 10L;
    private static final String CONTEXT_KEY_PREFIX = "ai:chat:context:";

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    @Override
    public void addContextMessage(Long userId, String conversationId, String role, String content) {
        validateInput(userId, conversationId, role, content);
        String key = buildKey(userId, conversationId);
        ChatContextMessage message = ChatContextMessage.builder()
                .role(role)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        redisUtil.lRightPush(key, message);
        trimContext(key);
        redisUtil.expire(key, CONTEXT_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<ChatContextMessage> getContextMessages(Long userId, String conversationId) {
        if (userId == null || !StringUtils.hasText(conversationId)) {
            return Collections.emptyList();
        }
        String key = buildKey(userId, conversationId);
        List<Object> rawList = redisUtil.lRange(key, 0, -1);
        if (rawList.isEmpty()) {
            return Collections.emptyList();
        }
        return rawList.stream()
                .map(this::convertToMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public boolean clearContext(Long userId, String conversationId) {
        if (userId == null || !StringUtils.hasText(conversationId)) {
            return false;
        }
        return redisUtil.delete(buildKey(userId, conversationId));
    }

    private String buildKey(Long userId, String conversationId) {
        return CONTEXT_KEY_PREFIX + userId + ":" + conversationId;
    }

    private void trimContext(String key) {
        redisUtil.lTrim(key, -MAX_CONTEXT_MESSAGES, -1);
    }

    private void validateInput(Long userId, String conversationId, String role, String content) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("role must not be blank");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    private ChatContextMessage convertToMessage(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ChatContextMessage message) {
            return message;
        }
        return objectMapper.convertValue(raw, ChatContextMessage.class);
    }
}
