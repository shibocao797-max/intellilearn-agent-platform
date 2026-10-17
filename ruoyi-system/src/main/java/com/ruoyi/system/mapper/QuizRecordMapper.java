package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.QuizRecord;
import com.ruoyi.system.domain.QuizQuestionQuery;
import com.ruoyi.system.domain.QuizWrongQuestion;

public interface QuizRecordMapper {
    int insertRecord(QuizRecord record);
    List<QuizRecord> selectByUser(Long userId, int limit);
    List<QuizRecord> selectByUserInRange(@Param("userId") Long userId, @Param("days") int days, @Param("limit") int limit);
    List<QuizRecord> selectByUserAndQuestion(Long userId, Long questionId);
    Map<String, Object> selectStats(Long userId);
    Map<String, Object> selectStatsInRange(@Param("userId") Long userId, @Param("days") int days);
    List<Map<String, Object>> selectPerformanceTrend(@Param("userId") Long userId, @Param("days") int days);
    List<QuizWrongQuestion> selectLatestWrongQuestions(@Param("userId") Long userId,
                                                        @Param("query") QuizQuestionQuery query);
    long countLatestWrongQuestions(@Param("userId") Long userId,
                                   @Param("query") QuizQuestionQuery query);
}
