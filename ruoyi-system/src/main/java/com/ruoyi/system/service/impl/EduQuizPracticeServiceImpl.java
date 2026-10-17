package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.ai.tool.BehaviorTool;
import com.ruoyi.system.domain.QuizPracticeItem;
import com.ruoyi.system.domain.QuizPracticeSession;
import com.ruoyi.system.domain.QuizQuestion;
import com.ruoyi.system.domain.QuizQuestionQuery;
import com.ruoyi.system.domain.QuizRecord;
import com.ruoyi.system.domain.QuizWrongQuestion;
import com.ruoyi.system.domain.StudentQuizQuestion;
import com.ruoyi.system.mapper.QuizPracticeSessionMapper;
import com.ruoyi.system.mapper.QuizQuestionMapper;
import com.ruoyi.system.mapper.QuizRecordMapper;
import com.ruoyi.system.service.IEduQuizPracticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session-backed learner practice flow. The service owns all authoritative
 * state: question availability, grading, submitted state and cumulative counts.
 */
@Service
public class EduQuizPracticeServiceImpl implements IEduQuizPracticeService
{
    private static final Logger log = LoggerFactory.getLogger(EduQuizPracticeServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_QUESTION_COUNT = 50;
    private static final int MAX_ANSWER_LENGTH = 20_000;
    private static final int MAX_TIME_SPENT_SECONDS = 8 * 60 * 60;
    private static final Set<String> QUESTION_TYPES = Set.of(
        "single_choice", "multi_choice", "fill_blank", "short_answer", "code");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> SOURCES = Set.of("BANK", "WRONG", "AI_CHAT", "RECOMMENDED");
    private static final Set<String> OBJECTIVE_TYPES = Set.of("single_choice", "multi_choice", "fill_blank");

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired
    private QuizPracticeSessionMapper practiceSessionMapper;

    @Autowired(required = false)
    private BehaviorTool behaviorTool;

    @Autowired(required = false)
    private ProfilePracticeVerificationService profilePracticeVerificationService;

    @Override
    public Map<String, Object> questionBank(QuizQuestionQuery query)
    {
        normalizeQuery(query);
        Map<String, Object> result = pageResult(
            quizQuestionMapper.selectStudentQuestions(query),
            quizQuestionMapper.countStudentQuestions(query), query);
        return result;
    }

    @Override
    public Map<String, Object> questionFacets()
    {
        Set<String> knowledgePoints = new TreeSet<>();
        for (String raw : quizQuestionMapper.selectStudentKnowledgePointJsons())
        {
            if (raw == null || raw.isBlank())
            {
                continue;
            }
            try
            {
                JSONArray values = JSON.parseArray(raw);
                if (values == null)
                {
                    continue;
                }
                for (Object value : values)
                {
                    if (value != null && !String.valueOf(value).isBlank())
                    {
                        knowledgePoints.add(String.valueOf(value));
                    }
                }
            }
            catch (Exception ignored)
            {
                // Old imported questions may contain malformed legacy JSON.
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionTypes", quizQuestionMapper.selectStudentQuestionTypes());
        result.put("difficulties", quizQuestionMapper.selectStudentDifficulties());
        result.put("knowledgePoints", new ArrayList<>(knowledgePoints));
        return result;
    }

    @Override
    public Map<String, Object> wrongQuestions(Long userId, QuizQuestionQuery query)
    {
        normalizeQuery(query);
        List<QuizWrongQuestion> rows = quizRecordMapper.selectLatestWrongQuestions(userId, query);
        return pageResult(rows, quizRecordMapper.countLatestWrongQuestions(userId, query), query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSession(Long userId, List<Long> questionIds, String source)
    {
        LinkedHashSet<Long> uniqueIds = normalizeQuestionIds(questionIds);
        if (practiceSessionMapper.selectActiveSessionByUserId(userId) != null)
        {
            throw new ServiceException("Please continue or finish the current practice session first");
        }

        for (Long questionId : uniqueIds)
        {
            if (quizQuestionMapper.selectStudentQuestionById(questionId) == null)
            {
                throw new ServiceException("Question is unavailable");
            }
        }

        QuizPracticeSession session = new QuizPracticeSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setSource(normalizeSource(source));
        session.setStatus("IN_PROGRESS");
        session.setTotalCount(uniqueIds.size());
        session.setCurrentPosition(1);
        practiceSessionMapper.insertSession(session);

        int position = 1;
        for (Long questionId : uniqueIds)
        {
            QuizQuestion question = quizQuestionMapper.selectQuestionById(questionId);
            QuizPracticeItem item = new QuizPracticeItem();
            item.setSessionId(session.getId());
            item.setUserId(userId);
            item.setQuestionId(questionId);
            item.setQuestionSnapshotJson(questionSnapshot(question));
            item.setPositionIndex(position++);
            item.setState("UNANSWERED");
            item.setDraftAnswer("");
            item.setFlagged(Boolean.FALSE);
            practiceSessionMapper.insertItem(item);
        }
        return sessionDetail(userId, session.getId());
    }

    @Override
    public Map<String, Object> activeSession(Long userId)
    {
        QuizPracticeSession session = practiceSessionMapper.selectActiveSessionByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session == null ? null : buildSessionView(userId, session));
        return result;
    }

    @Override
    public Map<String, Object> sessionDetail(Long userId, String sessionId)
    {
        return buildSessionView(userId, ownedSession(userId, sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveDraft(Long userId, String sessionId, Long questionId,
                                         Object draftAnswer, Boolean flagged, Integer currentPosition)
    {
        QuizPracticeSession session = lockedOwnedSession(userId, sessionId);
        requireInProgress(session);
        QuizPracticeItem item = lockedOwnedItem(userId, sessionId, questionId);
        if ("SUBMITTED".equals(item.getState()))
        {
            boolean draftChanged = draftAnswer != null
                && !textValue(draftAnswer).equals(item.getDraftAnswer() == null ? "" : item.getDraftAnswer());
            boolean flaggedChanged = flagged != null
                && flagged.booleanValue() != Boolean.TRUE.equals(item.getFlagged());
            if (draftChanged || flaggedChanged)
            {
                throw new ServiceException("A submitted answer cannot be changed");
            }
            updateCurrentPosition(session, sessionId, userId,
                currentPosition == null ? item.getPositionIndex() : currentPosition);
            return sessionDetail(userId, sessionId);
        }

        String answer = textValue(draftAnswer);
        validateAnswerLength(answer);
        item.setDraftAnswer(answer);
        item.setFlagged(flagged == null ? Boolean.TRUE.equals(item.getFlagged()) : flagged);
        item.setState(answer.isBlank() ? "UNANSWERED" : "DRAFT");
        if (practiceSessionMapper.updateItemDraft(item) != 1)
        {
            throw new ServiceException("Practice item draft could not be saved");
        }
        updateCurrentPosition(session, sessionId, userId,
            currentPosition == null ? item.getPositionIndex() : currentPosition);
        practiceSessionMapper.refreshSessionCounts(sessionId, userId);
        return sessionDetail(userId, sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long userId, String sessionId, Long questionId,
                                      Object userAnswer, Object timeSpent, String idempotencyKey)
    {
        QuizPracticeSession session = lockedOwnedSession(userId, sessionId);
        requireInProgress(session);
        QuizPracticeItem item = lockedOwnedItem(userId, sessionId, questionId);
        QuizQuestion question = questionFromSnapshot(item);
        if (question == null)
        {
            throw new ServiceException("Question is no longer available");
        }
        if ("SUBMITTED".equals(item.getState()))
        {
            return submittedResult(item, question, true);
        }

        String answer = textValue(userAnswer);
        if (answer.isBlank())
        {
            throw new ServiceException("Answer cannot be empty");
        }
        validateAnswerLength(answer);
        int seconds = parseTimeSpent(timeSpent);

        boolean objective = OBJECTIVE_TYPES.contains(question.getQuestionType());
        Boolean isCorrect = objective ? grade(question, answer) : null;
        boolean pendingReview = !objective;
        BigDecimal score = isCorrect == null ? null : (isCorrect ? new BigDecimal("100") : BigDecimal.ZERO);

        QuizRecord record = new QuizRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setSessionId(sessionId);
        record.setUserAnswer(answer);
        record.setIsCorrect(isCorrect);
        record.setScore(score);
        record.setTimeSpentSec(seconds);
        record.setAnswerTime(new Date());
        quizRecordMapper.insertRecord(record);

        item.setSubmittedAnswer(answer);
        item.setIsCorrect(isCorrect);
        item.setPendingReview(pendingReview);
        item.setScore(score);
        item.setTimeSpentSec(seconds);
        item.setQuizRecordId(record.getId());
        item.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
        if (practiceSessionMapper.markItemSubmitted(item) != 1)
        {
            throw new ServiceException("Practice answer could not be submitted");
        }
        updateCurrentPosition(session, sessionId, userId, item.getPositionIndex());
        practiceSessionMapper.refreshSessionCounts(sessionId, userId);
        logAnswerBehavior(userId, questionId, sessionId, seconds);
        return submittedResult(item, question, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> finish(Long userId, String sessionId, boolean abandon)
    {
        QuizPracticeSession session = lockedOwnedSession(userId, sessionId);
        boolean completedNow = false;
        if ("IN_PROGRESS".equals(session.getStatus()))
        {
            String status = abandon ? "ABANDONED" : "COMPLETED";
            if (practiceSessionMapper.finishSession(sessionId, userId, status) != 1)
            {
                throw new ServiceException("Practice session could not be finished");
            }
            completedNow = !abandon;
        }
        Map<String, Object> result = sessionDetail(userId, sessionId);
        if (completedNow && profilePracticeVerificationService != null)
        {
            scheduleProfileVerification(userId, sessionId);
        }
        return result;
    }

    private void scheduleProfileVerification(Long userId, String sessionId)
    {
        Runnable verification = () -> {
            try {
                profilePracticeVerificationService.verifyCompletedSession(userId, sessionId);
            } catch (Exception e) {
                log.error("练习画像验证失败: userId={}, sessionId={}, error={}",
                        userId, sessionId, e.getMessage(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { verification.run(); }
            });
        } else {
            verification.run();
        }
    }

    @Override
    public Map<String, Object> sessionResult(Long userId, String sessionId)
    {
        QuizPracticeSession session = ownedSession(userId, sessionId);
        Map<String, Object> result = buildSessionView(userId, session);
        List<QuizPracticeItem> items = practiceSessionMapper.selectItemsBySessionIdAndUserId(sessionId, userId);
        int objectiveAnswered = 0;
        int objectiveCorrect = 0;
        int pendingReview = 0;
        for (QuizPracticeItem item : items)
        {
            if (!"SUBMITTED".equals(item.getState()))
            {
                continue;
            }
            if (Boolean.TRUE.equals(item.getPendingReview()) || item.getIsCorrect() == null)
            {
                pendingReview++;
                continue;
            }
            objectiveAnswered++;
            if (Boolean.TRUE.equals(item.getIsCorrect()))
            {
                objectiveCorrect++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectiveAnsweredCount", objectiveAnswered);
        // Retain the original result field while callers move to the shared stats name.
        summary.put("objectiveSubmittedCount", objectiveAnswered);
        summary.put("objectiveCorrectCount", objectiveCorrect);
        summary.put("objectiveCorrectRate", objectiveAnswered == 0 ? 0 :
            Math.round(objectiveCorrect * 1000.0 / objectiveAnswered) / 10.0);
        summary.put("pendingReviewCount", pendingReview);
        result.put("result", summary);
        return result;
    }

    @Override
    public void requireSubmittedQuestion(Long userId, String sessionId, Long questionId)
    {
        ownedSession(userId, sessionId);
        if (questionId == null || questionId <= 0)
        {
            throw new ServiceException("Question id is invalid");
        }
        for (QuizPracticeItem item : practiceSessionMapper.selectItemsBySessionIdAndUserId(sessionId, userId))
        {
            if (questionId.equals(item.getQuestionId()) && "SUBMITTED".equals(item.getState()))
            {
                return;
            }
        }
        throw new ServiceException("Submit this question in the current practice session before requesting an explanation");
    }

    private Map<String, Object> pageResult(List<?> rows, long total, QuizQuestionQuery query)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("pageNum", query.getPageNum());
        result.put("pageSize", query.getPageSize());
        return result;
    }

    private Map<String, Object> buildSessionView(Long userId, QuizPracticeSession session)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", session.getId());
        result.put("source", session.getSource());
        result.put("status", session.getStatus());
        result.put("totalCount", session.getTotalCount());
        result.put("submittedCount", session.getSubmittedCount());
        result.put("correctCount", session.getCorrectCount());
        result.put("currentPosition", session.getCurrentPosition());
        result.put("startedAt", session.getStartedAt());
        result.put("lastActiveAt", session.getLastActiveAt());
        result.put("finishedAt", session.getFinishedAt());

        List<Map<String, Object>> items = new ArrayList<>();
        for (QuizPracticeItem item : practiceSessionMapper.selectItemsBySessionIdAndUserId(session.getId(), userId))
        {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("questionId", item.getQuestionId());
            view.put("position", item.getPositionIndex());
            view.put("state", item.getState());
            view.put("draftAnswer", item.getDraftAnswer());
            view.put("flagged", Boolean.TRUE.equals(item.getFlagged()));
            view.put("submittedAnswer", item.getSubmittedAnswer());
            view.put("isCorrect", item.getIsCorrect());
            view.put("pendingReview", Boolean.TRUE.equals(item.getPendingReview()));
            view.put("score", item.getScore());
            view.put("timeSpentSec", item.getTimeSpentSec());
            view.put("submittedAt", item.getSubmittedAt());
            QuizQuestion question = questionFromSnapshot(item);
            view.put("question", studentQuestion(question));
            if ("SUBMITTED".equals(item.getState()))
            {
                if (question != null)
                {
                    view.put("correctAnswer", question.getAnswer());
                    view.put("explanation", question.getExplanation());
                }
            }
            items.add(view);
        }
        result.put("items", items);
        return result;
    }

    private QuizQuestion questionFromSnapshot(QuizPracticeItem item)
    {
        if (item.getQuestionSnapshotJson() != null && !item.getQuestionSnapshotJson().isBlank())
        {
            try { return JSON.parseObject(item.getQuestionSnapshotJson(), QuizQuestion.class); }
            catch (Exception e) { log.warn("练习题快照解析失败，回退当前题目: itemId={}", item.getId()); }
        }
        return item.getQuestionId() == null ? null : quizQuestionMapper.selectQuestionById(item.getQuestionId());
    }

    private static StudentQuizQuestion studentQuestion(QuizQuestion question)
    {
        if (question == null) return null;
        StudentQuizQuestion view = new StudentQuizQuestion();
        view.setId(question.getId());
        view.setQuestionType(question.getQuestionType());
        view.setStem(question.getStem());
        view.setOptions(question.getOptions());
        view.setDifficulty(question.getDifficulty());
        view.setKnowledgePoints(question.getKnowledgePoints());
        return view;
    }

    private static String questionSnapshot(QuizQuestion question)
    {
        if (question == null) return null;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", question.getId());
        snapshot.put("questionType", question.getQuestionType());
        snapshot.put("stem", question.getStem());
        snapshot.put("options", question.getOptions());
        snapshot.put("answer", question.getAnswer());
        snapshot.put("explanation", question.getExplanation());
        snapshot.put("difficulty", question.getDifficulty());
        snapshot.put("knowledgePoints", question.getKnowledgePoints());
        return JSON.toJSONString(snapshot);
    }

    private Map<String, Object> submittedResult(QuizPracticeItem item, QuizQuestion question, boolean idempotent)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isCorrect", item.getIsCorrect());
        result.put("pendingReview", Boolean.TRUE.equals(item.getPendingReview()));
        result.put("score", item.getScore());
        result.put("correctAnswer", question.getAnswer());
        result.put("explanation", question.getExplanation());
        result.put("questionType", question.getQuestionType());
        result.put("recordId", item.getQuizRecordId());
        result.put("idempotent", idempotent);
        return result;
    }

    private QuizPracticeSession ownedSession(Long userId, String sessionId)
    {
        validateSessionId(sessionId);
        QuizPracticeSession session = practiceSessionMapper.selectSessionByIdAndUserId(sessionId, userId);
        if (session == null)
        {
            throw new ServiceException("Practice session not found");
        }
        return session;
    }

    private QuizPracticeSession lockedOwnedSession(Long userId, String sessionId)
    {
        validateSessionId(sessionId);
        QuizPracticeSession session = practiceSessionMapper.selectSessionForUpdateByIdAndUserId(sessionId, userId);
        if (session == null)
        {
            throw new ServiceException("Practice session not found");
        }
        return session;
    }

    private QuizPracticeItem lockedOwnedItem(Long userId, String sessionId, Long questionId)
    {
        if (questionId == null || questionId <= 0)
        {
            throw new ServiceException("Question id is invalid");
        }
        QuizPracticeItem item = practiceSessionMapper.selectItemForUpdate(sessionId, userId, questionId);
        if (item == null)
        {
            throw new ServiceException("Question does not belong to this practice session");
        }
        return item;
    }

    private void requireInProgress(QuizPracticeSession session)
    {
        if (!"IN_PROGRESS".equals(session.getStatus()))
        {
            throw new ServiceException("Practice session is already finished");
        }
    }

    private void updateCurrentPosition(QuizPracticeSession session, String sessionId, Long userId,
                                       Integer currentPosition)
    {
        if (currentPosition == null || currentPosition < 1 || currentPosition > session.getTotalCount())
        {
            throw new ServiceException("Current practice position is invalid");
        }
        if (practiceSessionMapper.updateCurrentPosition(sessionId, userId, currentPosition) != 1)
        {
            throw new ServiceException("Current practice position could not be saved");
        }
    }

    private LinkedHashSet<Long> normalizeQuestionIds(List<Long> questionIds)
    {
        if (questionIds == null || questionIds.isEmpty())
        {
            throw new ServiceException("At least one question is required");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long questionId : questionIds)
        {
            if (questionId == null || questionId <= 0)
            {
                throw new ServiceException("Question id is invalid");
            }
            ids.add(questionId);
        }
        if (ids.size() > MAX_QUESTION_COUNT)
        {
            throw new ServiceException("A practice session can contain at most " + MAX_QUESTION_COUNT + " questions");
        }
        return ids;
    }

    private void normalizeQuery(QuizQuestionQuery query)
    {
        if (query == null)
        {
            throw new ServiceException("Question query is required");
        }
        query.setPageNum(Math.max(1, query.getPageNum()));
        query.setPageSize(Math.max(1, Math.min(MAX_PAGE_SIZE, query.getPageSize())));
        query.setOffset((query.getPageNum() - 1) * query.getPageSize());
        query.setKeyword(trimToLength(query.getKeyword(), 100, "Keyword"));
        query.setKnowledgePoint(trimToLength(query.getKnowledgePoint(), 100, "Knowledge point"));
        query.setQuestionType(normalizeFilter(query.getQuestionType(), QUESTION_TYPES, "Question type"));
        query.setDifficulty(normalizeFilter(query.getDifficulty(), DIFFICULTIES, "Difficulty"));
    }

    private String normalizeSource(String source)
    {
        String normalized = source == null || source.isBlank() ? "BANK" : source.trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(normalized))
        {
            throw new ServiceException("Practice source is invalid");
        }
        return normalized;
    }

    private String normalizeFilter(String value, Set<String> allowed, String label)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized))
        {
            throw new ServiceException(label + " is invalid");
        }
        return normalized;
    }

    private String trimToLength(String value, int maxLength, String label)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength)
        {
            throw new ServiceException(label + " is too long");
        }
        return trimmed;
    }

    private boolean grade(QuizQuestion question, String userAnswer)
    {
        if ("multi_choice".equals(question.getQuestionType()))
        {
            return normalizeMultiChoice(userAnswer).equals(normalizeMultiChoice(question.getAnswer()));
        }
        if ("fill_blank".equals(question.getQuestionType()))
        {
            for (String accepted : question.getAnswer().split("[|;；]"))
            {
                if (normalizeAnswer(userAnswer).equals(normalizeAnswer(accepted)))
                {
                    return true;
                }
            }
            return false;
        }
        return normalizeAnswer(userAnswer).equals(normalizeAnswer(question.getAnswer()));
    }

    private String normalizeMultiChoice(String answer)
    {
        String compact = normalizeAnswer(answer);
        if (compact.isEmpty())
        {
            return compact;
        }
        Set<String> choices = new TreeSet<>();
        if (compact.matches("[A-Z]+"))
        {
            for (char choice : compact.toCharArray())
            {
                choices.add(String.valueOf(choice));
            }
        }
        else
        {
            for (String part : compact.split("[,，、;；|/]+"))
            {
                if (!part.isBlank())
                {
                    choices.add(part.replaceFirst("^([A-Z])[.)、].*$", "$1"));
                }
            }
        }
        return String.join(",", choices);
    }

    private String normalizeAnswer(String answer)
    {
        return answer == null ? "" : answer.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private int parseTimeSpent(Object raw)
    {
        if (raw == null || String.valueOf(raw).isBlank())
        {
            return 0;
        }
        final int seconds;
        try
        {
            seconds = new BigDecimal(String.valueOf(raw)).intValueExact();
        }
        catch (NumberFormatException | ArithmeticException e)
        {
            throw new ServiceException("Time spent must be a whole number of seconds");
        }
        if (seconds < 0 || seconds > MAX_TIME_SPENT_SECONDS)
        {
            throw new ServiceException("Time spent is outside the accepted range");
        }
        return seconds;
    }

    private String textValue(Object value)
    {
        if (value == null)
        {
            return "";
        }
        if (value instanceof Collection<?> collection)
        {
            return collection.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
        }
        return String.valueOf(value);
    }

    private void validateAnswerLength(String answer)
    {
        if (answer.length() > MAX_ANSWER_LENGTH)
        {
            throw new ServiceException("Answer is too long");
        }
    }

    private String normalizeIdempotencyKey(String value)
    {
        if (value == null || value.isBlank())
        {
            return UUID.randomUUID().toString();
        }
        String key = value.trim();
        if (key.length() > 64)
        {
            throw new ServiceException("Idempotency key is too long");
        }
        return key;
    }

    private void validateSessionId(String sessionId)
    {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64)
        {
            throw new ServiceException("Practice session id is invalid");
        }
    }

    private void logAnswerBehavior(Long userId, Long questionId, String sessionId, int seconds)
    {
        if (behaviorTool == null)
        {
            return;
        }
        try
        {
            behaviorTool.logBehavior(userId, "ANSWER_QUIZ", "quiz", questionId, sessionId, seconds);
        }
        catch (Exception ignored)
        {
            // Analytics must not make an otherwise valid learner submission fail.
        }
    }
}
