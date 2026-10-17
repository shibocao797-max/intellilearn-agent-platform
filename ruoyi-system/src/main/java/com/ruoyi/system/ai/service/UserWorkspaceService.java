package com.ruoyi.system.ai.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduUserWorkspace;
import com.ruoyi.system.mapper.EduUserWorkspaceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Persists user-facing project/session workspace metadata.
 */
@Service
public class UserWorkspaceService
{
    private static final String DEFAULT_PROJECT_NAME = "默认项目";
    private static final String UNTITLED_PROJECT_NAME = "未命名项目";
    public static final String DEFAULT_COORDINATOR_NICKNAME = "协调智能体";
    private static final int MAX_COORDINATOR_NICKNAME_CODE_POINTS = 20;

    @Autowired
    private EduUserWorkspaceMapper workspaceMapper;

    public Map<String, Object> getWorkspace(Long userId)
    {
        EduUserWorkspace workspace = workspaceMapper.selectByUserId(userId);
        if (workspace == null || workspace.getWorkspaceJson() == null || workspace.getWorkspaceJson().isBlank())
        {
            return emptyWorkspace();
        }

        Object parsed = JSON.parse(workspace.getWorkspaceJson());
        if (!(parsed instanceof Map))
        {
            return emptyWorkspace();
        }

        return normalizeWorkspace(castMap(parsed));
    }

    public void saveWorkspace(Long userId, Map<String, Object> payload)
    {
        Map<String, Object> normalized = normalizeWorkspace(payload);
        EduUserWorkspace existing = workspaceMapper.selectByUserId(userId);

        if (existing == null)
        {
            EduUserWorkspace workspace = new EduUserWorkspace();
            workspace.setUserId(userId);
            workspace.setWorkspaceJson(JSON.toJSONString(normalized));
            workspaceMapper.insertWorkspace(workspace);
            return;
        }

        existing.setWorkspaceJson(JSON.toJSONString(normalized));
        workspaceMapper.updateWorkspaceByUserId(existing);
    }

    private Map<String, Object> emptyWorkspace()
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projects", new ArrayList<>());
        payload.put("sessionNames", new LinkedHashMap<>());
        payload.put("activeProjectId", "");
        payload.put("activeSessionId", "");
        payload.put("coordinatorNickname", DEFAULT_COORDINATOR_NICKNAME);
        return payload;
    }

    private Map<String, Object> normalizeWorkspace(Map<String, Object> payload)
    {
        Map<String, Object> normalized = emptyWorkspace();
        if (payload == null)
        {
            return normalized;
        }

        normalized.put("projects", normalizeProjects(payload.get("projects")));
        normalized.put("sessionNames", normalizeSessionNames(payload.get("sessionNames")));
        normalized.put("activeProjectId", asString(payload.get("activeProjectId")));
        normalized.put("activeSessionId", asString(payload.get("activeSessionId")));
        normalized.put("coordinatorNickname", normalizeCoordinatorNickname(payload.get("coordinatorNickname")));
        return normalized;
    }

    private String normalizeCoordinatorNickname(Object rawNickname)
    {
        String nickname = rawNickname == null ? "" : String.valueOf(rawNickname).strip();
        if (nickname.isEmpty())
        {
            return DEFAULT_COORDINATOR_NICKNAME;
        }
        if (nickname.codePoints().anyMatch(Character::isISOControl))
        {
            throw new ServiceException("协调智能体昵称不能包含控制字符");
        }
        if (nickname.codePointCount(0, nickname.length()) > MAX_COORDINATOR_NICKNAME_CODE_POINTS)
        {
            throw new ServiceException("协调智能体昵称不能超过20个字符");
        }
        return nickname;
    }

    private List<Map<String, Object>> normalizeProjects(Object rawProjects)
    {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (!(rawProjects instanceof Collection<?> collection))
        {
            return normalized;
        }

        for (Object item : collection)
        {
            if (!(item instanceof Map<?, ?> rawProject))
            {
                continue;
            }

            String id = asString(rawProject.get("id"));
            if (id.isBlank())
            {
                continue;
            }

            Map<String, Object> project = new LinkedHashMap<>();
            project.put("id", id);
            project.put("name", defaultString(asString(rawProject.get("name")), UNTITLED_PROJECT_NAME));
            project.put("sessionIds", normalizeSessionIds(rawProject.get("sessionIds")));
            project.put("collapsed", asBoolean(rawProject.get("collapsed")));
            project.put("createdAt", defaultString(asString(rawProject.get("createdAt")), ""));
            normalized.add(project);
        }

        if (normalized.isEmpty())
        {
            return normalized;
        }

        boolean hasDefaultProject = normalized.stream()
            .anyMatch(project -> DEFAULT_PROJECT_NAME.equals(project.get("name")));
        if (!hasDefaultProject)
        {
            return normalized;
        }

        return normalized;
    }

    private List<String> normalizeSessionIds(Object rawSessionIds)
    {
        LinkedHashSet<String> sessionIds = new LinkedHashSet<>();
        if (rawSessionIds instanceof Collection<?> collection)
        {
            for (Object item : collection)
            {
                String value = asString(item);
                if (!value.isBlank())
                {
                    sessionIds.add(value);
                }
            }
        }
        return new ArrayList<>(sessionIds);
    }

    private Map<String, String> normalizeSessionNames(Object rawSessionNames)
    {
        Map<String, String> sessionNames = new LinkedHashMap<>();
        if (!(rawSessionNames instanceof Map<?, ?> rawMap))
        {
            return sessionNames;
        }

        rawMap.forEach((key, value) -> {
            String sessionId = asString(key);
            String name = asString(value);
            if (!sessionId.isBlank() && !name.isBlank())
            {
                sessionNames.put(sessionId, name);
            }
        });
        return sessionNames;
    }

    private Map<String, Object> castMap(Object value)
    {
        return value instanceof Map<?, ?> rawMap ? new LinkedHashMap<>((Map<String, Object>) rawMap) : new LinkedHashMap<>();
    }

    private String asString(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean asBoolean(Object value)
    {
        if (value instanceof Boolean bool)
        {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String defaultString(String value, String fallback)
    {
        return value == null || value.isBlank() ? fallback : value;
    }
}
