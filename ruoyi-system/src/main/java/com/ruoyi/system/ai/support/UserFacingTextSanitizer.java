package com.ruoyi.system.ai.support;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes provider and model identifiers from content that can reach a learner.
 * Provider names remain available in server configuration and diagnostics.
 */
public final class UserFacingTextSanitizer {

    private static final Pattern MODEL_NAME = Pattern.compile(
            "\\bAgnes[-_\\s]*2(?:\\.0)?[-_\\s]*Flash\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_PROVIDER_NAME = Pattern.compile(
            "\\bAgnes[-_\\s]*(?:Image|Video)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROVIDER_NAME = Pattern.compile(
            "\\bAgnes\\b", Pattern.CASE_INSENSITIVE);
    /**
     * Media URLs are machine-readable data, not learner-facing copy.  Keep
     * them intact while sanitizing surrounding text; otherwise a provider
     * name in a hostname/path becomes an invalid URL and media cards break.
     */
    private static final Pattern URL_TOKEN = Pattern.compile(
            "(?i)https?://[^\\s\"'<>]+");
    private static final Set<String> MACHINE_URL_KEYS = Set.of(
            "url", "sourceurl", "coverurl", "videourl", "imageurl", "audiourl",
            "mediaurl", "fileurl", "downloadurl", "previewurl", "thumbnailurl",
            "posterurl", "outputurl", "rawurl", "streamurl", "uploadurl");
    private static final String CORRUPTED_OUTPUT_DOMAIN =
            "platform-outputs.AI \u52A9\u624B-ai.space";
    private static final String OUTPUT_DOMAIN = "platform-outputs.agnes-ai.space";
    private static final String CORRUPTED_VIDEO_PATH = "/videos/\u5A92\u4F53\u670D\u52A1-v2.0/";
    private static final String VIDEO_PATH = "/videos/agnes-video-v2.0/";
    private static final String MODEL_REPLACEMENT = "AI 助手";
    private static final String MEDIA_REPLACEMENT = "媒体服务";

    private UserFacingTextSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        List<String> protectedUrls = new ArrayList<>();
        Matcher urlMatcher = URL_TOKEN.matcher(value);
        StringBuffer textWithoutUrls = new StringBuffer();
        while (urlMatcher.find()) {
            int index = protectedUrls.size();
            protectedUrls.add(sanitizeMediaUrl(urlMatcher.group()));
            urlMatcher.appendReplacement(textWithoutUrls,
                    Matcher.quoteReplacement("__USER_FACING_URL_" + index + "__"));
        }
        urlMatcher.appendTail(textWithoutUrls);

        String sanitized = MODEL_NAME.matcher(textWithoutUrls.toString()).replaceAll(MODEL_REPLACEMENT);
        sanitized = MEDIA_PROVIDER_NAME.matcher(sanitized).replaceAll(MEDIA_REPLACEMENT);
        sanitized = PROVIDER_NAME.matcher(sanitized).replaceAll(MODEL_REPLACEMENT);
        for (int index = 0; index < protectedUrls.size(); index++) {
            sanitized = sanitized.replace("__USER_FACING_URL_" + index + "__", protectedUrls.get(index));
        }
        return sanitizeMediaUrl(sanitized);
    }

    /** Recursively sanitizes maps/lists used by JSON responses and SSE payloads. */
    public static Object sanitizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof String string) {
            return sanitize(string);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                // Keep URL-valued protocol fields byte-for-byte stable. URL
                // strings nested in free-form content are protected by
                // sanitize(String) above.
                if (isMachineUrlKey(key) && item instanceof String) {
                    sanitized.put(key, sanitizeMediaUrl((String) item));
                } else {
                    sanitized.put(key, sanitizeValue(item));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                sanitized.add(sanitizeValue(item));
            }
            return sanitized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                sanitized.add(sanitizeValue(Array.get(value, index)));
            }
            return sanitized;
        }
        return value;
    }

    private static boolean isMachineUrlKey(Object key) {
        return key != null && MACHINE_URL_KEYS.contains(
                String.valueOf(key).replace("_", "").toLowerCase(Locale.ROOT));
    }

    /**
     * Repairs URLs persisted by older builds that redacted provider names in
     * the output hostname/path. Valid URLs are returned unchanged.
     */
    public static String sanitizeMediaUrl(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replace(CORRUPTED_OUTPUT_DOMAIN, OUTPUT_DOMAIN)
                .replace(CORRUPTED_VIDEO_PATH, VIDEO_PATH);
    }
}
