package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.QuizPracticeItem;
import com.ruoyi.system.domain.QuizPracticeSession;

/** Mapper for resumable learner practice sessions and their question state. */
public interface QuizPracticeSessionMapper
{
    int insertSession(QuizPracticeSession session);

    int insertItem(QuizPracticeItem item);

    QuizPracticeSession selectSessionByIdAndUserId(@Param("sessionId") String sessionId,
                                                    @Param("userId") Long userId);

    QuizPracticeSession selectSessionForUpdateByIdAndUserId(@Param("sessionId") String sessionId,
                                                             @Param("userId") Long userId);

    QuizPracticeSession selectActiveSessionByUserId(Long userId);

    List<QuizPracticeItem> selectItemsBySessionIdAndUserId(@Param("sessionId") String sessionId,
                                                            @Param("userId") Long userId);

    QuizPracticeItem selectItemForUpdate(@Param("sessionId") String sessionId,
                                          @Param("userId") Long userId,
                                          @Param("questionId") Long questionId);

    int updateItemDraft(QuizPracticeItem item);

    int markItemSubmitted(QuizPracticeItem item);

    int refreshSessionCounts(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    int updateCurrentPosition(@Param("sessionId") String sessionId, @Param("userId") Long userId,
                              @Param("currentPosition") Integer currentPosition);

    int finishSession(@Param("sessionId") String sessionId, @Param("userId") Long userId,
                      @Param("status") String status);
}
