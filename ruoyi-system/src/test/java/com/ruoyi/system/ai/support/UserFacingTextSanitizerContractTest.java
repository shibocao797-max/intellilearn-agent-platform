package com.ruoyi.system.ai.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Executable contract test for keeping media URLs intact during redaction. */
public final class UserFacingTextSanitizerContractTest
{
    public static void main(String[] args)
    {
        String imageUrl = "https://platform-outputs.agnes-ai.space/images/t2i/demo.png";
        String videoUrl = "https://platform-outputs.agnes-ai.space/videos/agnes-video-v2.0/demo.mp4";
        String corruptedVideoUrl = "https://platform-outputs.AI 助手-ai.space/videos/媒体服务-v2.0/demo.mp4";

        require(imageUrl.equals(UserFacingTextSanitizer.sanitize(imageUrl)),
            "plain media URL was changed by sanitizer");
        require(videoUrl.equals(UserFacingTextSanitizer.sanitize(corruptedVideoUrl)),
            "legacy corrupted media URL was not repaired");
        require(UserFacingTextSanitizer.sanitize("由 Agnes 生成的配图").contains("AI 助手"),
            "provider name in learner-facing copy was not redacted");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "agnes");
        payload.put("url", imageUrl);
        payload.put("video_url", videoUrl);
        payload.put("message", "Agnes image is ready");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) UserFacingTextSanitizer.sanitizeValue(payload);
        require(imageUrl.equals(sanitized.get("url")), "url field was changed");
        require(videoUrl.equals(sanitized.get("video_url")), "snake_case video_url field was changed");
        require("AI 助手".equals(sanitized.get("provider")), "provider field was not redacted");
        require(String.valueOf(sanitized.get("message")).contains("媒体服务"),
            "nested learner-facing message was not redacted");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
