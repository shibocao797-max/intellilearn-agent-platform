package com.ruoyi.system.ai.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small JSON helpers for LLM-driven agents.
 */
final class AgentJsonSupport {

    private AgentJsonSupport() {
    }

    static JSONObject parseFirstObject(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("LLM output is empty");
        }
        String cleaned = stripMarkdownFence(text.trim());
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON object found in LLM output");
        }
        return JSON.parseObject(cleaned.substring(start, end + 1));
    }

    static String stripMarkdownFence(String text) {
        if (text == null) return "";
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                cleaned = cleaned.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank()) {
            result.add(text);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                if (item instanceof JSONObject json) {
                    result.add(new LinkedHashMap<>(json));
                } else if (item instanceof Map<?, ?> map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        return result;
    }

    static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static double numberValue(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars)) + "...";
    }

    static List<Map<String, Object>> compactResources(List<Map<String, Object>> resources, int contentLimit) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (resources == null) return result;
        for (Map<String, Object> resource : resources) {
            result.add(compactResource(resource, contentLimit));
        }
        return result;
    }

    static Map<String, Object> compactResource(Map<String, Object> resource, int contentLimit) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (resource == null) return item;
        copyIfPresent(resource, item, "agentName");
        copyIfPresent(resource, item, "resourceType");
        copyIfPresent(resource, item, "displayName");
        copyIfPresent(resource, item, "persistedId");
        copyIfPresent(resource, item, "quality");
        copyIfPresent(resource, item, "success");
        copyIfPresent(resource, item, "sourceUrl");
        copyIfPresent(resource, item, "videoId");
        copyIfPresent(resource, item, "mediaStatus");
        String content = stringValue(resource.get("content"));
        if (!content.isBlank()) {
            item.put("content", truncate(content, contentLimit));
        }
        return item;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }
}
