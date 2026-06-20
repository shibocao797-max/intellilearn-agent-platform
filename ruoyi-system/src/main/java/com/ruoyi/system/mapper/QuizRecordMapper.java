package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.QuizRecord;

public interface QuizRecordMapper {
    int insertRecord(QuizRecord record);
    List<QuizRecord> selectByUser(Long userId, int limit);
    List<QuizRecord> selectByUserAndQuestion(Long userId, Long questionId);
    Map<String, Object> selectStats(Long userId);
}
