package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.util.AiApiUtil;

/**
 * LangChain4j ChatModel backed by DeepSeek chat completions.
 */
public class DeepSeekChatModel extends ProviderChatModel {

    public DeepSeekChatModel(AiApiUtil aiApiUtil, AiModelSelectionStrategy modelSelectionStrategy) {
        super(aiApiUtil, modelSelectionStrategy, AiModelProvider.DEEPSEEK);
    }
}
