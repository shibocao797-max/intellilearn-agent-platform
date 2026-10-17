package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.LearnerProfileEvidence;
import java.util.List;

public interface LearnerProfileEvidenceMapper {
    int insertEvidence(LearnerProfileEvidence evidence);
    List<LearnerProfileEvidence> selectEvidenceByProfile(@org.apache.ibatis.annotations.Param("userId") Long userId,
                                                          @org.apache.ibatis.annotations.Param("profileId") Long profileId,
                                                          @org.apache.ibatis.annotations.Param("profileVersion") Integer profileVersion);
}
