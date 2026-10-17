package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.EduUserAccountProfile;

/** Persistence mapper for account-owned profile and preference data. */
public interface EduUserAccountProfileMapper
{
    EduUserAccountProfile selectByUserId(Long userId);

    int insertAccountProfile(EduUserAccountProfile profile);

    int updateAccountProfileByUserId(EduUserAccountProfile profile);
}
