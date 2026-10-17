package com.ruoyi.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class MimoAsrService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

    @Value("${MIMO_ASR_ENABLED:true}")
    private boolean enabled;

    @Value("${MIMO_ASR_BASE_URL:https://token-plan-cn.xiaomimimo.com/v1}")
    private String baseUrl;

    @Value("${MIMO_ASR_API_KEY:}")
    private String apiKey;

    @Value("${MIMO_ASR_MODEL:mimo-v2.5-asr}")
    private String model;

    public String transcribe(String audioBase64, String mimeType) {
        if (!enabled) {
            throw new ServiceException("MiMo ASR is disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException("MiMo ASR api key is missing");
        }
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new ServiceException("Audio payload is empty");
        }
        if (!isSupportedMimeType(mimeType)) {
            throw new ServiceException("Unsupported audio mime type");
        }

        int approxBytes = (audioBase64.length() * 3) / 4;
        if (approxBytes > MAX_AUDIO_BYTES) {
            throw new ServiceException("Audio file exceeds MiMo 10MB limit");
        }

        JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("messages", new JSONArray());
        JSONObject message = new JSONObject();
        message.put("role", "user");
        JSONArray content = new JSONArray();
        JSONObject inputAudio = new JSONObject();
        inputAudio.put("type", "input_audio");
        inputAudio.put("input_audio", Map.of("data", "data:" + mimeType + ";base64," + audioBase64));
        content.add(inputAudio);
        message.put("content", content);
        root.getJSONArray("messages").add(message);
        root.put("asr_options", Map.of("language", "zh"));

        Request request = new Request.Builder()
                .url(normalizeBaseUrl(baseUrl) + "/chat/completions")
                .header("api-key", apiKey)
                .post(RequestBody.create(root.toJSONString().getBytes(StandardCharsets.UTF_8), JSON_MEDIA_TYPE))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw buildRemoteError(responseBody, response.code());
            }

            JSONObject payload = JSON.parseObject(responseBody);
            JSONArray choices = payload.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ServiceException("MiMo ASR returned no choices");
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject messagePayload = firstChoice != null ? firstChoice.getJSONObject("message") : null;
            String text = messagePayload != null ? messagePayload.getString("content") : null;
            if (text == null) {
                throw new ServiceException("MiMo ASR returned empty transcript");
            }
            return text.trim();
        } catch (IOException e) {
            throw new ServiceException("MiMo ASR request failed: " + e.getMessage());
        }
    }

    private boolean isSupportedMimeType(String mimeType) {
        return "audio/wav".equalsIgnoreCase(mimeType) || "audio/mpeg".equalsIgnoreCase(mimeType) || "audio/mp3".equalsIgnoreCase(mimeType);
    }

    private String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private ServiceException buildRemoteError(String responseBody, int statusCode) {
        try {
            JSONObject payload = JSON.parseObject(responseBody);
            JSONObject error = payload != null ? payload.getJSONObject("error") : null;
            String message = error != null ? error.getString("message") : null;
            return new ServiceException("MiMo ASR failed(" + statusCode + "): " + (message == null ? responseBody : message));
        } catch (Exception ignored) {
            return new ServiceException("MiMo ASR failed(" + statusCode + "): " + responseBody);
        }
    }
}
