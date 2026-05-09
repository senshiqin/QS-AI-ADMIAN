package com.qs.ai.admian.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis utility for common String/Hash/List operations.
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private static final String CONTEXT_KEY_PREFIX = "ai:context:";
    private static final String SESSION_KEY_PREFIX = "ai:session:";

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return clazz.isInstance(value) ? (T) value : null;
    }

    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    public Long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return redisTemplate.delete(keys);
    }

    public boolean expire(String key, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public boolean hSet(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean hSet(String key, String hashKey, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
            return expire(key, timeout, unit);
        } catch (Exception ex) {
            return false;
        }
    }

    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public Map<Object, Object> hGetAll(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries == null ? Collections.emptyMap() : entries;
    }

    public Long hDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    public boolean hHasKey(String key, String hashKey) {
        Boolean result = redisTemplate.opsForHash().hasKey(key, hashKey);
        return Boolean.TRUE.equals(result);
    }

    public Long lRightPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public Long lRightPushAll(String key, List<?> values) {
        if (values == null || values.isEmpty()) {
            return redisTemplate.opsForList().size(key);
        }
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    public void lRightPushAllTrimExpire(String key,
                                        List<?> values,
                                        long maxSize,
                                        long timeout,
                                        TimeUnit unit) {
        if (values == null || values.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                operations.opsForList().rightPushAll(key, values);
                operations.opsForList().trim(key, -maxSize, -1);
                operations.expire(key, timeout, unit);
                return null;
            }
        });
    }

    public List<Object> lRange(String key, long start, long end) {
        List<Object> list = redisTemplate.opsForList().range(key, start, end);
        return list == null ? Collections.emptyList() : list;
    }

    public void lTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    public Long lSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0L : size;
    }

    public Object lLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public Long lRemove(String key, long count, Object value) {
        return redisTemplate.opsForList().remove(key, count, value);
    }

    public boolean cacheConversationContext(Long userId, String conversationId, Object context, long ttlMinutes) {
        String key = CONTEXT_KEY_PREFIX + userId + ":" + conversationId;
        return set(key, context, ttlMinutes, TimeUnit.MINUTES);
    }

    public Object getConversationContext(Long userId, String conversationId) {
        String key = CONTEXT_KEY_PREFIX + userId + ":" + conversationId;
        return get(key);
    }

    public boolean clearConversationContext(Long userId, String conversationId) {
        String key = CONTEXT_KEY_PREFIX + userId + ":" + conversationId;
        return delete(key);
    }

    public boolean cacheUserSession(Long userId, Object sessionData, long ttlMinutes) {
        String key = SESSION_KEY_PREFIX + userId;
        return set(key, sessionData, ttlMinutes, TimeUnit.MINUTES);
    }

    public Object getUserSession(Long userId) {
        String key = SESSION_KEY_PREFIX + userId;
        return get(key);
    }

    public boolean clearUserSession(Long userId) {
        String key = SESSION_KEY_PREFIX + userId;
        return delete(key);
    }
}
