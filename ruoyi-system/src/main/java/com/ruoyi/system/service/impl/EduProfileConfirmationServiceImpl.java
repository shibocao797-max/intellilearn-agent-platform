package com.ruoyi.system.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.ai.tool.ProfileTool;
import com.ruoyi.system.domain.LearnerProfile;
import com.ruoyi.system.domain.LearnerProfileConfirmation;
import com.ruoyi.system.mapper.LearnerProfileConfirmationMapper;
import com.ruoyi.system.mapper.LearnerProfileMapper;
import com.ruoyi.system.service.IEduProfileConfirmationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EduProfileConfirmationServiceImpl implements IEduProfileConfirmationService
{
    private static final Set<String> CONFIRMABLE_DIMENSIONS = Set.of("knowledgeLevel", "weakPoints", "strengths");
    private static final Set<String> ALLOWED_STATUSES = Set.of("CONFIRMED", "REJECTED");

    @Autowired
    private LearnerProfileMapper profileMapper;

    @Autowired
    private LearnerProfileConfirmationMapper confirmationMapper;

    @Autowired
    private ProfileTool profileTool;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmDimension(Long userId, String dimensionKey, Integer profileVersion,
                                                String status, String rejectReason)
    {
        if (userId == null || userId <= 0) throw new ServiceException("User is not authenticated");
        if (!CONFIRMABLE_DIMENSIONS.contains(dimensionKey)) throw new ServiceException("This profile dimension cannot be confirmed");
        if (profileVersion == null || profileVersion < 1) throw new ServiceException("Profile version is required");
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) throw new ServiceException("Invalid profile confirmation status");

        LearnerProfile profile = profileMapper.selectProfileByUserId(userId);
        if (profile == null || profile.getId() == null || !profileVersion.equals(profile.getVersion())) {
            throw new ServiceException("Profile has changed. Reload the latest profile before confirming it");
        }

        Map<String, Object> view = profileTool.buildProfileView(userId, profile);
        boolean currentDimensionExists = false;
        Object rawInferences = view.get("inferences");
        if (rawInferences instanceof List<?> inferences) {
            for (Object raw : inferences) {
                if (raw instanceof Map<?, ?> item && dimensionKey.equals(String.valueOf(item.get("key")))) {
                    currentDimensionExists = true;
                    break;
                }
            }
        }
        if (!currentDimensionExists) throw new ServiceException("The profile conclusion is empty or no longer current");

        LearnerProfileConfirmation confirmation = new LearnerProfileConfirmation();
        confirmation.setUserId(userId);
        confirmation.setProfileId(profile.getId());
        confirmation.setProfileVersion(profileVersion);
        confirmation.setDimensionKey(dimensionKey);
        confirmation.setStatus(normalizedStatus);
        confirmation.setRejectReason(normalizedStatus.equals("REJECTED") ? trim(rejectReason, 500) : null);
        confirmation.setConfirmedBy(userId);
        confirmation.setConfirmedAt(new Date());
        confirmationMapper.upsert(confirmation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimensionKey", dimensionKey);
        result.put("profileVersion", profileVersion);
        result.put("status", normalizedStatus);
        result.put("profileView", profileTool.buildProfileView(userId, profile));
        return result;
    }

    private static String trim(String value, int maxLength)
    {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
