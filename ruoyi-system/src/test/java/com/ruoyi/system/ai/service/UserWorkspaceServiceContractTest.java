package com.ruoyi.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduUserWorkspace;
import com.ruoyi.system.mapper.EduUserWorkspaceMapper;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executable contract test for user-scoped coordinator nickname preferences. */
public final class UserWorkspaceServiceContractTest
{
    public static void main(String[] args) throws Exception
    {
        InMemoryWorkspaceMapper mapper = new InMemoryWorkspaceMapper();
        UserWorkspaceService service = new UserWorkspaceService();
        inject(service, "workspaceMapper", mapper);

        require(UserWorkspaceService.DEFAULT_COORDINATOR_NICKNAME.equals(
            service.getWorkspace(1L).get("coordinatorNickname")), "default nickname was not returned");

        service.saveWorkspace(1L, Map.of("coordinatorNickname", "  学习搭子  "));
        service.saveWorkspace(2L, Map.of("coordinatorNickname", "Java教练"));
        require("学习搭子".equals(service.getWorkspace(1L).get("coordinatorNickname")),
            "nickname was not trimmed and persisted");
        require("Java教练".equals(service.getWorkspace(2L).get("coordinatorNickname")),
            "user-scoped nickname was not isolated");

        service.saveWorkspace(1L, Map.of("coordinatorNickname", "   "));
        require(UserWorkspaceService.DEFAULT_COORDINATOR_NICKNAME.equals(
            service.getWorkspace(1L).get("coordinatorNickname")), "blank nickname did not restore the default");

        String twentyCodePoints = "学".repeat(19) + "😀";
        service.saveWorkspace(1L, Map.of("coordinatorNickname", twentyCodePoints));
        require(twentyCodePoints.equals(service.getWorkspace(1L).get("coordinatorNickname")),
            "20-code-point nickname was rejected");

        requireRejected(() -> service.saveWorkspace(1L,
            Map.of("coordinatorNickname", twentyCodePoints + "习")), "21-code-point nickname was accepted");
        requireRejected(() -> service.saveWorkspace(1L,
            Map.of("coordinatorNickname", "学习\n搭子")), "control character was accepted");
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

    private static final class InMemoryWorkspaceMapper implements EduUserWorkspaceMapper
    {
        private final Map<Long, EduUserWorkspace> workspaces = new LinkedHashMap<>();

        @Override
        public EduUserWorkspace selectByUserId(Long userId)
        {
            EduUserWorkspace workspace = workspaces.get(userId);
            if (workspace == null)
            {
                return null;
            }
            EduUserWorkspace copy = new EduUserWorkspace();
            copy.setId(workspace.getId());
            copy.setUserId(workspace.getUserId());
            copy.setWorkspaceJson(workspace.getWorkspaceJson());
            return copy;
        }

        @Override
        public int insertWorkspace(EduUserWorkspace workspace)
        {
            workspace.setId((long) workspaces.size() + 1);
            workspaces.put(workspace.getUserId(), copy(workspace));
            return 1;
        }

        @Override
        public int updateWorkspaceByUserId(EduUserWorkspace workspace)
        {
            if (!workspaces.containsKey(workspace.getUserId()))
            {
                return 0;
            }
            workspaces.put(workspace.getUserId(), copy(workspace));
            return 1;
        }

        private EduUserWorkspace copy(EduUserWorkspace source)
        {
            EduUserWorkspace copy = new EduUserWorkspace();
            copy.setId(source.getId());
            copy.setUserId(source.getUserId());
            copy.setWorkspaceJson(JSON.toJSONString(JSON.parse(source.getWorkspaceJson())));
            return copy;
        }
    }
}
