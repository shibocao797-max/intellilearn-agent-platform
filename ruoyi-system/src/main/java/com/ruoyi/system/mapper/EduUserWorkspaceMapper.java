package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.EduUserWorkspace;

/**
 * Workspace persistence mapper.
 */
public interface EduUserWorkspaceMapper
{
    EduUserWorkspace selectByUserId(Long userId);

    int insertWorkspace(EduUserWorkspace workspace);

    int updateWorkspaceByUserId(EduUserWorkspace workspace);
}
