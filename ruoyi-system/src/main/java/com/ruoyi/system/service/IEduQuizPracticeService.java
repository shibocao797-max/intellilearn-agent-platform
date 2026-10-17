package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.QuizQuestionQuery;

/** Application service for the learner-facing question bank and practice flow. */
public interface IEduQuizPracticeService
{
    Map<String, Object> questionBank(QuizQuestionQuery query);

    Map<String, Object> questionFacets();

    Map<String, Object> wrongQuestions(Long userId, QuizQuestionQuery query);

    Map<String, Object> createSession(Long userId, List<Long> questionIds, String source);

    Map<String, Object> activeSession(Long userId);

    Map<String, Object> sessionDetail(Long userId, String sessionId);

    Map<String, Object> saveDraft(Long userId, String sessionId, Long questionId,
                                  Object draftAnswer, Boolean flagged, Integer currentPosition);

    Map<String, Object> submit(Long userId, String sessionId, Long questionId,
                               Object userAnswer, Object timeSpent, String idempotencyKey);

    Map<String, Object> finish(Long userId, String sessionId, boolean abandon);

    Map<String, Object> sessionResult(Long userId, String sessionId);

    void requireSubmittedQuestion(Long userId, String sessionId, Long questionId);
}
