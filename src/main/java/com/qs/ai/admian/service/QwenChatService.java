package com.qs.ai.admian.service;

import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.QwenChatResult;
import com.qs.ai.admian.service.dto.QwenStreamHandler;

import java.util.List;

/**
 * Qwen chat completion service.
 */
public interface QwenChatService {

    QwenChatResult chat(String model, List<AiChatMessage> messages, Double temperature);

    QwenChatResult streamChat(String model,
                              List<AiChatMessage> messages,
                              Double temperature,
                              QwenStreamHandler streamHandler);
}
