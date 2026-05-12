package com.qs.ai.admian.langchain4j;

import com.qs.ai.admian.service.AiModelSelectionStrategy;
import com.qs.ai.admian.service.dto.AiModelProvider;
import com.qs.ai.admian.util.AiApiUtil;

/**
 * LangChain4j ChatModel backed by DashScope Qwen chat completions.
 */
public class QwenChatModel extends ProviderChatModel {

    public QwenChatModel(AiApiUtil aiApiUtil, AiModelSelectionStrategy modelSelectionStrategy) {
        super(aiApiUtil, modelSelectionStrategy, AiModelProvider.QWEN);
    }
}
