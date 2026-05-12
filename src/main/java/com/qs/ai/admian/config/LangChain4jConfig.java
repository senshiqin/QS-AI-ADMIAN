package com.qs.ai.admian.config;

import com.qs.ai.admian.langchain4j.QwenChatModel;
import com.qs.ai.admian.langchain4j.DeepSeekChatModel;
import com.qs.ai.admian.langchain4j.OllamaLocalChatModel;
import com.qs.ai.admian.langchain4j.QwenEmbeddingModel;
import com.qs.ai.admian.util.AiApiUtil;
import com.qs.ai.admian.util.AiEmbeddingUtil;
import com.qs.ai.admian.util.OllamaChatUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j model beans.
 */
@Configuration
public class LangChain4jConfig {

    @Bean
    public EmbeddingModel qwenEmbeddingModel(AiEmbeddingUtil aiEmbeddingUtil,
                                             DashScopeProperties dashScopeProperties) {
        return new QwenEmbeddingModel(aiEmbeddingUtil, dashScopeProperties.getEmbeddingDimensions());
    }

    @Bean
    public ChatModel qwenChatModel(AiApiUtil aiApiUtil) {
        return new QwenChatModel(aiApiUtil);
    }

    @Bean
    public ChatModel deepSeekChatModel(AiApiUtil aiApiUtil) {
        return new DeepSeekChatModel(aiApiUtil);
    }

    @Bean
    public ChatModel ollamaChatModel(OllamaChatUtil ollamaChatUtil) {
        return new OllamaLocalChatModel(ollamaChatUtil);
    }
}
