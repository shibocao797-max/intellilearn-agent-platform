package com.ruoyi.system.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.ai.tool.ProfileTool;
import com.ruoyi.system.domain.LearnerProfile;
import com.ruoyi.system.domain.LearnerProfileConfirmation;
import com.ruoyi.system.mapper.LearnerProfileConfirmationMapper;
import com.ruoyi.system.mapper.LearnerProfileMapper;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable contract test for profile ownership, versioning and idempotent resolution. */
public final class EduProfileConfirmationServiceContractTest
{
    public static void main(String[] args) throws Exception
    {
        LearnerProfile profile = new LearnerProfile();
        profile.setId(9L);
        profile.setUserId(7L);
        profile.setVersion(3);
        profile.setProfileJson("{\"dimensions\":{\"knowledgeLevel\":{\"value\":\"基础\"}}}");

        FakeConfirmationMapper confirmations = new FakeConfirmationMapper();
        EduProfileConfirmationServiceImpl service = new EduProfileConfirmationServiceImpl();
        inject(service, "profileMapper", new FakeProfileMapper(profile));
        inject(service, "confirmationMapper", confirmations);
        inject(service, "profileTool", new StubProfileTool());

        Map<String, Object> first = service.confirmDimension(7L, "knowledgeLevel", 3, "CONFIRMED", null);
        Map<String, Object> replay = service.confirmDimension(7L, "knowledgeLevel", 3, "CONFIRMED", null);
        require("CONFIRMED".equals(first.get("status")), "confirmation status was not returned");
        require("CONFIRMED".equals(replay.get("status")), "idempotent replay changed status");
        require(confirmations.rows.size() == 1, "idempotent replay created another confirmation");

        expectFailure(() -> service.confirmDimension(7L, "knowledgeLevel", 2, "CONFIRMED", null),
                "stale profile version was accepted");
        expectFailure(() -> service.confirmDimension(8L, "knowledgeLevel", 3, "CONFIRMED", null),
                "another user confirmed the profile");
        expectFailure(() -> service.confirmDimension(7L, "learningGoal", 3, "CONFIRMED", null),
                "non-confirmable preference was accepted");
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void expectFailure(ThrowingRunnable action, String message)
    {
        try { action.run(); }
        catch (ServiceException expected) { return; }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run(); }

    private static final class FakeProfileMapper implements LearnerProfileMapper
    {
        private final LearnerProfile profile;
        private FakeProfileMapper(LearnerProfile profile) { this.profile = profile; }
        @Override public LearnerProfile selectProfileByUserId(Long userId) { return profile.getUserId().equals(userId) ? profile : null; }
        @Override public int insertProfile(LearnerProfile value) { return 0; }
        @Override public int updateProfile(LearnerProfile value) { return 0; }
        @Override public boolean existsByUserId(Long userId) { return profile.getUserId().equals(userId); }
        @Override public List<LearnerProfile> selectHistoryByUserId(Long userId) { return List.of(); }
    }

    private static final class FakeConfirmationMapper implements LearnerProfileConfirmationMapper
    {
        private final Map<String, LearnerProfileConfirmation> rows = new LinkedHashMap<>();
        @Override public List<LearnerProfileConfirmation> selectByProfile(Long userId, Long profileId, Integer version) { return List.copyOf(rows.values()); }
        @Override public LearnerProfileConfirmation selectByDimension(Long userId, Long profileId, Integer version, String key) { return rows.get(key); }
        @Override public int upsert(LearnerProfileConfirmation value) { rows.put(value.getDimensionKey(), value); return 1; }
    }

    private static final class StubProfileTool extends ProfileTool
    {
        @Override public Map<String, Object> buildProfileView(Long userId, LearnerProfile profile)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", "knowledgeLevel");
            item.put("value", "基础");
            item.put("profileVersion", profile.getVersion());
            return Map.of("inferences", List.of(item));
        }
    }
}
