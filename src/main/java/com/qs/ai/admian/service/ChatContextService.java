package com.qs.ai.admian.service;

import com.qs.ai.admian.service.dto.ChatContextMessage;

import java.util.List;

/**
 * Manage AI chat context in Redis list.
 */
public interface ChatContextService {

    /**
     * Add one chat message into context list and refresh TTL to 1 hour.
     *
     * @param userId user id
     * @param conversationId conversation id
     * @param role message role, such as user/assistant/system
     * @param content message content
     */
    void addContextMessage(Long userId, String conversationId, String role, String content);

    /**
     * Add multiple messages and trim context in one Redis pipeline.
     *
     * @param userId user id
     * @param conversationId conversation id
     * @param messages ordered messages
     */
    void addContextMessages(Long userId, String conversationId, List<ChatContextMessage> messages);

    /**
     * Load full context list for one conversation.
     *
     * @param userId user id
     * @param conversationId conversation id
     * @return ordered context messages
     */
    List<ChatContextMessage> getContextMessages(Long userId, String conversationId);

    /**
     * Clear context list for one conversation.
     *
     * @param userId user id
     * @param conversationId conversation id
     * @return true when deleted successfully
     */
    boolean clearContext(Long userId, String conversationId);
}
