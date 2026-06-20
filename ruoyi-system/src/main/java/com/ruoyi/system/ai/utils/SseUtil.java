package com.ruoyi.system.ai.utils;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * 流式输出工具类 — StreamingChatModel 回调封装
 *
 * @author edu-agent
 */
public class SseUtil {

    private static final Logger log = LoggerFactory.getLogger(SseUtil.class);

    @FunctionalInterface
    public interface TokenCallback { void onToken(String token) throws IOException; }

    @FunctionalInterface
    public interface CompleteCallback { void onComplete(String fullResponse); }

    @FunctionalInterface
    public interface ErrorCallback { void onError(Throwable error); }

    /**
     * 通过 StreamingChatModel 异步流式生成
     */
    public static void streamAsync(
            StreamingChatModel streamingModel,
            String systemPrompt,
            String userPrompt,
            TokenCallback tokenCallback,
            CompleteCallback completeCallback,
            ErrorCallback errorCallback) {

        new Thread(() -> {
            try {
                streamingModel.chat(
                    List.of(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)),
                    new StreamingChatResponseHandler() {
                        private final StringBuilder full = new StringBuilder();

                        @Override
                        public void onPartialResponse(String partial) {
                            full.append(partial);
                            try { tokenCallback.onToken(partial); }
                            catch (IOException e) { log.error("Token callback IO error: {}", e.getMessage()); }
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            completeCallback.onComplete(full.toString());
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("Streaming error: {}", error.getMessage());
                            errorCallback.onError(error);
                        }
                    }
                );
            } catch (Exception e) {
                log.error("Streaming call failed: {}", e.getMessage(), e);
                errorCallback.onError(e);
            }
        }, "sse-stream").start();
    }
}
