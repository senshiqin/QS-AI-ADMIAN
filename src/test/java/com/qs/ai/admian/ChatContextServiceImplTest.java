package com.qs.ai.admian;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.service.impl.ChatContextServiceImpl;
import com.qs.ai.admian.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis chat context trimming behavior.
 */
class ChatContextServiceImplTest {

    @Test
    void addContextMessageShouldKeepOnlyLatestTenMessages() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        ChatContextServiceImpl service = new ChatContextServiceImpl(redisUtil, new ObjectMapper());

        service.addContextMessage(1L, "conv-trim-001", "user", "hello");

        String expectedKey = "ai:chat:context:1:conv-trim-001";
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisUtil).lRightPush(eq(expectedKey), messageCaptor.capture());
        verify(redisUtil).lTrim(expectedKey, -10L, -1L);
        verify(redisUtil).expire(expectedKey, 60L, TimeUnit.MINUTES);
        assertEquals("ChatContextMessage", messageCaptor.getValue().getClass().getSimpleName());
    }
}
