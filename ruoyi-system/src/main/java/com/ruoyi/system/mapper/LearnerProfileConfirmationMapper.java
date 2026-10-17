package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.LearnerProfileConfirmation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LearnerProfileConfirmationMapper
{
    List<LearnerProfileConfirmation> selectByProfile(@Param("userId") Long userId,
                                                     @Param("profileId") Long profileId,
                                                     @Param("profileVersion") Integer profileVersion);

    LearnerProfileConfirmation selectByDimension(@Param("userId") Long userId,
                                                 @Param("profileId") Long profileId,
                                                 @Param("profileVersion") Integer profileVersion,
                                                 @Param("dimensionKey") String dimensionKey);

    int upsert(LearnerProfileConfirmation confirmation);
}
