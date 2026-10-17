package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.LearnerProfileSnapshot;
import java.util.List;

public interface LearnerProfileSnapshotMapper {
    int insertSnapshot(LearnerProfileSnapshot snapshot);
    List<LearnerProfileSnapshot> selectSnapshotsByUserId(Long userId);
}
