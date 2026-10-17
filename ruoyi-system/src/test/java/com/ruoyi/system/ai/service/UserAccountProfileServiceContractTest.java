package com.ruoyi.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduUserAccountProfile;
import com.ruoyi.system.mapper.EduUserAccountProfileMapper;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executable contract test for account ownership and preference validation. */
public final class UserAccountProfileServiceContractTest
{
    public static void main(String[] args) throws Exception
    {
        InMemoryAccountProfileMapper mapper = new InMemoryAccountProfileMapper();
        UserAccountProfileService service = new UserAccountProfileService();
        inject(service, "accountProfileMapper", mapper);

        Map<String, Object> empty = service.getProfile(1L);
        require(Boolean.FALSE.equals(empty.get("hasRemoteData")), "empty account was marked remote");

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("notificationsEnabled", false);
        preferences.put("autoSyncEnabled", true);
        preferences.put("focusMode", true);
        service.saveProfile(1L, Map.of("bio", "account one", "preferences", preferences));
        service.saveProfile(2L, Map.of("bio", "account two"));

        Map<String, Object> accountOne = service.getProfile(1L);
        Map<String, Object> accountTwo = service.getProfile(2L);
        require(Boolean.TRUE.equals(accountOne.get("hasRemoteData")), "saved account was not marked remote");
        require("account one".equals(accountOne.get("bio")), "account one bio was not saved");
        require("account two".equals(accountTwo.get("bio")), "account data crossed user boundary");
        require("account one".equals(service.getProfile(1L).get("bio")), "account two changed account one");

        requireRejected(() -> service.saveProfile(1L,
            Map.of("preferences", Map.of("notAllowed", true))), "unknown preference was accepted");
        requireRejected(() -> service.saveProfile(1L,
            Map.of("bio", "x".repeat(UserAccountProfileService.MAX_BIO_CODE_POINTS + 1))),
            "oversized bio was accepted");
        requireRejected(() -> service.saveProfile(1L,
            Map.of("bio", "safe\u0001text")), "control character was accepted");

        service.saveProfile(1L, Map.of("userId", 2L, "bio", "still account one"));
        require("still account one".equals(service.getProfile(1L).get("bio")),
            "request userId changed the authenticated owner");
        require("account two".equals(service.getProfile(2L).get("bio")),
            "request userId modified another account");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void requireRejected(Runnable action, String message)
    {
        try
        {
            action.run();
        }
        catch (ServiceException expected)
        {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static final class InMemoryAccountProfileMapper implements EduUserAccountProfileMapper
    {
        private final Map<Long, EduUserAccountProfile> profiles = new LinkedHashMap<>();

        @Override
        public EduUserAccountProfile selectByUserId(Long userId)
        {
            return copy(profiles.get(userId));
        }

        @Override
        public int insertAccountProfile(EduUserAccountProfile profile)
        {
            profile.setId((long) profiles.size() + 1);
            profiles.put(profile.getUserId(), copy(profile));
            return 1;
        }

        @Override
        public int updateAccountProfileByUserId(EduUserAccountProfile profile)
        {
            if (!profiles.containsKey(profile.getUserId()))
            {
                return 0;
            }
            profiles.put(profile.getUserId(), copy(profile));
            return 1;
        }

        private EduUserAccountProfile copy(EduUserAccountProfile source)
        {
            if (source == null)
            {
                return null;
            }
            EduUserAccountProfile copy = new EduUserAccountProfile();
            copy.setId(source.getId());
            copy.setUserId(source.getUserId());
            copy.setBio(source.getBio());
            copy.setPreferencesJson(source.getPreferencesJson() == null
                ? null : JSON.toJSONString(JSON.parse(source.getPreferencesJson())));
            return copy;
        }
    }
}
