package com.ruoyi.system.ai.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置 — 创建 ChatModel 和 StreamingChatModel Bean
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${langchain4j.open-ai.chat-model.base-url:https://apihub.agnes-ai.com/v1}")
    private String chatBaseUrl;
    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String chatApiKey;
    @Value("${langchain4j.open-ai.chat-model.model-name:agnes-2.0-flash}")
    private String chatModelName;
    @Value("${langchain4j.open-ai.chat-model.max-tokens:16384}")
    private int chatMaxTokens;
    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private double chatTemperature;
    @Value("${langchain4j.open-ai.chat-model.timeout:600s}")
    private Duration chatTimeout;

    @Value("${langchain4j.open-ai.streaming-chat-model.base-url:https://apihub.agnes-ai.com/v1}")
    private String streamBaseUrl;
    @Value("${langchain4j.open-ai.streaming-chat-model.api-key:}")
    private String streamApiKey;
    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:agnes-2.0-flash}")
    private String streamModelName;
    @Value("${langchain4j.open-ai.streaming-chat-model.max-tokens:8192}")
    private int streamMaxTokens;
    @Value("${langchain4j.open-ai.streaming-chat-model.temperature:0.7}")
    private double streamTemperature;
    @Value("${langchain4j.open-ai.streaming-chat-model.timeout:300s}")
    private Duration streamTimeout;

    @Bean
    public ChatModel chatLanguageModel() {
        log.info("Initializing ChatModel: baseUrl={}, model={}, apiKeyConfigured={}",
                chatBaseUrl, chatModelName, hasText(chatApiKey));
        return OpenAiChatModel.builder()
                .baseUrl(chatBaseUrl)
                .apiKey(chatApiKey)
                .modelName(chatModelName)
                .maxTokens(chatMaxTokens)
                .temperature(chatTemperature)
                .timeout(chatTimeout)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        log.info("Initializing StreamingChatModel: baseUrl={}, model={}, apiKeyConfigured={}",
                streamBaseUrl, streamModelName, hasText(streamApiKey));
        return OpenAiStreamingChatModel.builder()
                .baseUrl(streamBaseUrl)
                .apiKey(streamApiKey)
                .modelName(streamModelName)
                .maxTokens(streamMaxTokens)
                .temperature(streamTemperature)
                .timeout(streamTimeout)
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
