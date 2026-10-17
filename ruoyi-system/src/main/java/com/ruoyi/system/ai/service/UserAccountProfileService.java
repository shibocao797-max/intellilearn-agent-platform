package com.ruoyi.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduUserAccountProfile;
import com.ruoyi.system.mapper.EduUserAccountProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists the account-owned profile and UI preferences used by the student
 * frontend. The caller is responsible for obtaining the user id from the
 * authenticated security context; this service never accepts an id from JSON.
 */
@Service
public class UserAccountProfileService
{
    public static final int MAX_BIO_CODE_POINTS = 240;

    private static final Set<String> PREFERENCE_FIELDS = Set.of(
        "menuItems", "notificationsEnabled", "autoSyncEnabled", "focusMode");

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
        "userId", "bio", "preferences", "menuItems", "notificationsEnabled", "autoSyncEnabled", "focusMode");

    private static final Set<String> MENU_FIELDS = Set.of("key", "enabled");

    private static final List<String> MENU_KEYS = List.of(
        "chat", "agent-dispatch", "profile", "resources", "quiz", "report");

    @Autowired
    private EduUserAccountProfileMapper accountProfileMapper;

    /** Returns the normalized profile for one authenticated account. */
    public Map<String, Object> getProfile(Long userId)
    {
        requireUserId(userId);
        EduUserAccountProfile existing = accountProfileMapper.selectByUserId(userId);
        return toView(userId, existing);
    }

    /**
     * Saves profile fields supplied by the caller. Omitted fields retain their
     * current value, while an explicit empty bio clears the stored bio.
     */
    public Map<String, Object> saveProfile(Long userId, Map<String, Object> payload)
    {
        requireUserId(userId);

        Map<String, Object> body = payload == null ? Map.of() : payload;
        for (String field : body.keySet())
        {
            if (!TOP_LEVEL_FIELDS.contains(field))
            {
                throw new ServiceException("unsupported account profile field: " + field);
            }
        }
        EduUserAccountProfile existing = accountProfileMapper.selectByUserId(userId);
        Map<String, Object> current = toView(userId, existing);

        String bio = String.valueOf(current.get("bio"));
        if (body.containsKey("bio"))
        {
            bio = normalizeBio(body.get("bio"));
        }

        Map<String, Object> preferences = castMap(current.get("preferences"));
        if (body.containsKey("preferences"))
        {
            Object rawPreferences = body.get("preferences");
            if (!(rawPreferences instanceof Map<?, ?>))
            {
                throw new ServiceException("preferences must be an object");
            }
            preferences = normalizePreferences(rawPreferences, preferences, true);
        }
        else if (containsPreferenceField(body))
        {
            // Accept a flattened body as a compatibility convenience, but
            // still pass only the allow-listed fields to the validator.
            preferences = normalizePreferences(preferenceFieldsFrom(body), preferences, true);
        }

        EduUserAccountProfile next = existing == null ? new EduUserAccountProfile() : existing;
        next.setUserId(userId);
        next.setBio(bio);
        next.setPreferencesJson(JSON.toJSONString(preferences));

        int affected;
        if (existing == null)
        {
            affected = accountProfileMapper.insertAccountProfile(next);
        }
        else
        {
            affected = accountProfileMapper.updateAccountProfileByUserId(next);
        }
        if (affected <= 0)
        {
            throw new ServiceException("account profile could not be saved");
        }
        return toView(userId, next);
    }

    private Map<String, Object> toView(Long userId, EduUserAccountProfile existing)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        // This flag lets the client migrate an old local cache only when no
        // server-owned row exists. A row, including an intentionally empty
        // profile, always wins over device-local state.
        result.put("hasRemoteData", existing != null);
        result.put("bio", existing == null ? "" : normalizeBio(existing.getBio()));
        result.put("preferences", existing == null
            ? defaultPreferences()
            : readStoredPreferences(existing.getPreferencesJson()));
        return result;
    }

    private Map<String, Object> readStoredPreferences(String preferencesJson)
    {
        Map<String, Object> defaults = defaultPreferences();
        if (preferencesJson == null || preferencesJson.isBlank())
        {
            return defaults;
        }
        try
        {
            Object parsed = JSON.parse(preferencesJson);
            return normalizePreferences(parsed, defaults, false);
        }
        catch (RuntimeException ignored)
        {
            // A malformed legacy row must not prevent the account from
            // opening settings; it falls back to safe defaults on read.
            return defaults;
        }
    }

    private Map<String, Object> normalizePreferences(Object raw,
                                                      Map<String, Object> base,
                                                      boolean strict)
    {
        if (!(raw instanceof Map<?, ?> rawMap))
        {
            if (strict)
            {
                throw new ServiceException("preferences must be an object");
            }
            return defaultPreferences();
        }

        Map<String, Object> normalized = defaultPreferences();
        Map<String, Object> baseMap = base == null ? defaultPreferences() : base;
        normalized.put("menuItems", normalizeMenuItems(baseMap.get("menuItems"), null, false));
        normalized.put("notificationsEnabled", booleanValue(baseMap.get("notificationsEnabled"), true, false));
        normalized.put("autoSyncEnabled", booleanValue(baseMap.get("autoSyncEnabled"), true, false));
        normalized.put("focusMode", booleanValue(baseMap.get("focusMode"), false, false));

        for (Map.Entry<?, ?> entry : rawMap.entrySet())
        {
            String field = String.valueOf(entry.getKey());
            if (!PREFERENCE_FIELDS.contains(field))
            {
                if (strict)
                {
                    throw new ServiceException("unsupported preference field: " + field);
                }
                continue;
            }

            Object value = entry.getValue();
            switch (field)
            {
                case "menuItems" -> normalized.put("menuItems",
                    normalizeMenuItems(value, normalized.get("menuItems"), strict));
                case "notificationsEnabled" -> normalized.put("notificationsEnabled",
                    booleanValue(value, true, strict));
                case "autoSyncEnabled" -> normalized.put("autoSyncEnabled",
                    booleanValue(value, true, strict));
                case "focusMode" -> normalized.put("focusMode",
                    booleanValue(value, false, strict));
                default -> throw new IllegalStateException("unhandled preference field: " + field);
            }
        }

        return normalized;
    }

    private List<Map<String, Object>> normalizeMenuItems(Object raw,
                                                         Object fallback,
                                                         boolean strict)
    {
        Map<String, Boolean> enabledByKey = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();

        Object fallbackItems = fallback;
        if (fallbackItems == null)
        {
            fallbackItems = defaultPreferences().get("menuItems");
        }
        if (fallbackItems instanceof Collection<?> collection)
        {
            for (Object item : collection)
            {
                if (!(item instanceof Map<?, ?> map))
                {
                    continue;
                }
                String key = String.valueOf(map.get("key"));
                if (!MENU_KEYS.contains(key) || enabledByKey.containsKey(key))
                {
                    continue;
                }
                enabledByKey.put(key, booleanValue(map.get("enabled"), true, false));
                order.add(key);
            }
        }
        for (String key : MENU_KEYS)
        {
            if (!enabledByKey.containsKey(key))
            {
                enabledByKey.put(key, true);
                order.add(key);
            }
        }

        if (raw instanceof Collection<?> collection)
        {
            Map<String, Boolean> submitted = new LinkedHashMap<>();
            for (Object item : collection)
            {
                if (!(item instanceof Map<?, ?> map))
                {
                    if (strict)
                    {
                        throw new ServiceException("menuItems must contain objects");
                    }
                    continue;
                }
                if (strict)
                {
                    for (Object key : map.keySet())
                    {
                        if (!MENU_FIELDS.contains(String.valueOf(key)))
                        {
                            throw new ServiceException("unsupported menu item field: " + key);
                        }
                    }
                }

                String key = String.valueOf(map.get("key"));
                if (!MENU_KEYS.contains(key))
                {
                    if (strict)
                    {
                        throw new ServiceException("unsupported menu item: " + key);
                    }
                    continue;
                }
                if (submitted.containsKey(key))
                {
                    throw new ServiceException("duplicate menu item: " + key);
                }
                submitted.put(key, booleanValue(map.get("enabled"), true, strict));
            }

            if (!submitted.isEmpty())
            {
                List<String> submittedOrder = new ArrayList<>(submitted.keySet());
                submittedOrder.addAll(order.stream().filter(key -> !submitted.containsKey(key)).toList());
                order = submittedOrder;
                submitted.forEach(enabledByKey::put);
            }
        }
        else if (raw == null && strict)
        {
            throw new ServiceException("menuItems cannot be null");
        }
        else if (raw != null && strict)
        {
            throw new ServiceException("menuItems must be an array");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : order)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            // Chat is the required entry and can never be hidden.
            item.put("enabled", "chat".equals(key) || Boolean.TRUE.equals(enabledByKey.get(key)));
            result.add(item);
        }
        return result;
    }

    private boolean booleanValue(Object raw, boolean fallback, boolean strict)
    {
        if (raw == null)
        {
            if (strict)
            {
                throw new ServiceException("preference boolean cannot be null");
            }
            return fallback;
        }
        if (raw instanceof Boolean value)
        {
            return value;
        }
        if (raw instanceof String value && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)))
        {
            return Boolean.parseBoolean(value);
        }
        if (strict)
        {
            throw new ServiceException("preference value must be boolean");
        }
        return fallback;
    }

    private String normalizeBio(Object raw)
    {
        if (raw != null && !(raw instanceof CharSequence))
        {
            throw new ServiceException("bio must be a string");
        }
        String bio = raw == null ? "" : String.valueOf(raw).strip();
        if (bio.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
            && codePoint != '\n' && codePoint != '\r' && codePoint != '\t'))
        {
            throw new ServiceException("bio contains unsupported control characters");
        }
        if (bio.codePointCount(0, bio.length()) > MAX_BIO_CODE_POINTS)
        {
            throw new ServiceException("bio cannot exceed " + MAX_BIO_CODE_POINTS + " characters");
        }
        return bio;
    }

    private boolean containsPreferenceField(Map<String, Object> body)
    {
        return body.keySet().stream().anyMatch(PREFERENCE_FIELDS::contains);
    }

    private Map<String, Object> preferenceFieldsFrom(Map<String, Object> body)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : PREFERENCE_FIELDS)
        {
            if (body.containsKey(field))
            {
                result.put(field, body.get(field));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value)
    {
        return value instanceof Map<?, ?> rawMap
            ? new LinkedHashMap<>((Map<String, Object>) rawMap)
            : new LinkedHashMap<>();
    }

    private Map<String, Object> defaultPreferences()
    {
        List<Map<String, Object>> menuItems = new ArrayList<>();
        for (String key : MENU_KEYS)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("enabled", true);
            menuItems.add(item);
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("menuItems", menuItems);
        defaults.put("notificationsEnabled", true);
        defaults.put("autoSyncEnabled", true);
        defaults.put("focusMode", false);
        return defaults;
    }

    private void requireUserId(Long userId)
    {
        if (userId == null || userId <= 0)
        {
            throw new ServiceException("authenticated user is required");
        }
    }
}
