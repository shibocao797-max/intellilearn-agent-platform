package com.ruoyi.system.ai.media;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small Agnes API client for image/video resource agents.
 */
@Component
public class AgnesMediaClient {

    @Value("${media.agnes.image.base-url:https://apihub.agnes-ai.com}")
    private String imageBaseUrl;

    @Value("${media.agnes.image.api-key:}")
    private String imageApiKey;

    @Value("${media.agnes.image.model-name:agnes-image-2.1-flash}")
    private String imageModelName;

    @Value("${media.agnes.image.endpoint:/v1/images/generations}")
    private String imageEndpoint;

    @Value("${media.agnes.video.base-url:https://apihub.agnes-ai.com}")
    private String videoBaseUrl;

    @Value("${media.agnes.video.api-key:}")
    private String videoApiKey;

    @Value("${media.agnes.video.model-name:agnes-video-v2.0}")
    private String videoModelName;

    @Value("${media.agnes.video.create-endpoint:/v1/videos}")
    private String videoCreateEndpoint;

    @Value("${media.agnes.video.query-endpoint:/agnesapi}")
    private String videoQueryEndpoint;

    @Value("${media.agnes.video.poll-attempts:2}")
    private int videoPollAttempts;

    @Value("${media.agnes.video.poll-interval-ms:3000}")
    private long videoPollIntervalMs;

    @Value("${media.agnes.video.create-inline-poll:false}")
    private boolean videoCreateInlinePoll;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public boolean imageEnabled() {
        return hasText(imageApiKey);
    }

    public boolean videoEnabled() {
        return hasText(videoApiKey);
    }

    public Map<String, Object> generateImage(String prompt, String size) {
        if (!imageEnabled()) {
            return disabled("image", "AGNES_IMAGE_API_KEY is not configured");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", imageModelName);
            body.put("prompt", prompt);
            body.put("size", hasText(size) ? size : "1024x768");
            body.put("extra_body", JSONObject.of("response_format", "url"));

            JSONObject response = postJson(joinUrl(imageBaseUrl, imageEndpoint), imageApiKey, body);
            String url = null;
            JSONArray data = response.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONObject first = data.getJSONObject(0);
                url = first.getString("url");
            }
            Map<String, Object> result = baseResult("image");
            result.put("status", hasText(url) ? "completed" : "unknown");
            result.put("model", imageModelName);
            result.put("url", url);
            result.put("raw", response);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = baseResult("image");
            result.put("status", "fallback");
            result.put("model", imageModelName);
            result.put("url", null);
            result.put("message", "Image generation service unavailable: " + trim(e.getMessage(), 300));
            result.put("prompt", prompt);
            return result;
        }
    }

    public Map<String, Object> createVideo(String prompt, String imageUrl) {
        if (!videoEnabled()) {
            return disabled("video", "AGNES_VIDEO_API_KEY is not configured");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", videoModelName);
            body.put("prompt", prompt);
            body.put("width", 1152);
            body.put("height", 768);
            body.put("num_frames", 121);
            body.put("frame_rate", 24);
            if (hasText(imageUrl)) {
                body.put("image", imageUrl);
                body.put("mode", "ti2vid");
            }

            JSONObject created = postJson(joinUrl(videoBaseUrl, videoCreateEndpoint), videoApiKey, body);
            String videoId = firstText(created, "video_id", "task_id", "id");

            Map<String, Object> result = baseResult("video");
            result.put("status", hasText(videoId) ? "submitted" : "unknown");
            result.put("model", videoModelName);
            result.put("videoId", videoId);
            result.put("createResponse", created);

            if (videoCreateInlinePoll && hasText(videoId)) {
                Map<String, Object> latest = pollVideo(videoId);
                result.putAll(latest);
            }
            return result;
        } catch (Exception e) {
            Map<String, Object> result = baseResult("video");
            result.put("status", "fallback");
            result.put("model", videoModelName);
            result.put("videoId", null);
            result.put("videoUrl", null);
            result.put("message", "Video generation service unavailable: " + trim(e.getMessage(), 300));
            result.put("prompt", prompt);
            return result;
        }
    }

    public Map<String, Object> queryVideo(String videoId) {
        if (!videoEnabled()) {
            return disabled("video", "AGNES_VIDEO_API_KEY is not configured");
        }
        if (!hasText(videoId)) {
            return disabled("video", "video_id is empty");
        }
        String query = joinUrl(videoBaseUrl, videoQueryEndpoint)
                + "?video_id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                + "&model_name=" + URLEncoder.encode(videoModelName, StandardCharsets.UTF_8);
        JSONObject response = getJson(query, videoApiKey);
        Map<String, Object> result = baseResult("video");
        result.put("videoId", videoId);
        result.put("status", normalizeStatus(response.getString("status")));
        result.put("videoUrl", firstText(response, "video_url", "url", "output_url", "download_url", "remixed_from_video_id"));
        String remixedFromVideoId = response.getString("remixed_from_video_id");
        if (hasText(remixedFromVideoId)) {
            result.put("remixedFromVideoId", remixedFromVideoId);
        }
        result.put("queryResponse", response);
        return result;
    }

    public Map<String, Object> pollVideoStatus(String videoId, int attempts, long intervalMs) {
        return pollVideo(videoId, attempts, intervalMs);
    }

    private Map<String, Object> pollVideo(String videoId) {
        return pollVideo(videoId, videoPollAttempts, videoPollIntervalMs);
    }

    private Map<String, Object> pollVideo(String videoId, int attempts, long intervalMs) {
        Map<String, Object> latest = new LinkedHashMap<>();
        for (int i = 0; i < Math.max(1, attempts); i++) {
            try {
                if (i > 0) {
                    Thread.sleep(Math.max(0, intervalMs));
                }
                latest = queryVideo(videoId);
                latest.put("pollAttempt", i + 1);
                latest.put("pollAttempts", Math.max(1, attempts));
                String status = String.valueOf(latest.get("status"));
                if ("completed".equals(status) || "failed".equals(status)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                latest.put("status", "pending");
                latest.put("message", "video polling interrupted");
                break;
            } catch (Exception e) {
                latest.put("status", "pending");
                latest.put("message", e.getMessage());
                break;
            }
        }
        if (latest.isEmpty()) {
            latest.put("status", "submitted");
        }
        return latest;
    }

    private JSONObject postJson(String url, String apiKey, JSONObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                .build();
        return execute(request);
    }

    private JSONObject getJson(String url, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return execute(request);
    }

    private JSONObject execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Agnes API HTTP " + response.statusCode() + ": " + trim(response.body(), 500));
            }
            return JSON.parseObject(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Agnes API call failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> baseResult(String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "agnes");
        result.put("type", type);
        return result;
    }

    private Map<String, Object> disabled(String type, String message) {
        Map<String, Object> result = baseResult(type);
        result.put("status", "config_missing");
        result.put("message", message);
        return result;
    }

    private String normalizeStatus(String status) {
        if (!hasText(status)) return "pending";
        String lower = status.toLowerCase();
        if (lower.contains("complete") || lower.contains("success")) return "completed";
        if (lower.contains("fail") || lower.contains("error")) return "failed";
        return lower;
    }

    private String firstText(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            String value = object.getString(key);
            if (hasText(value)) return value;
        }
        return null;
    }

    private String joinUrl(String baseUrl, String endpoint) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String path = endpoint == null ? "" : endpoint;
        if (!path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
