package com.ruoyi.system.service.impl;

import com.ruoyi.system.ai.tool.ProfileTool;
import com.ruoyi.system.domain.QuizPracticeItem;
import com.ruoyi.system.domain.QuizPracticeSession;
import com.ruoyi.system.domain.QuizQuestion;
import com.ruoyi.system.domain.QuizQuestionQuery;
import com.ruoyi.system.domain.StudentQuizQuestion;
import com.ruoyi.system.mapper.QuizPracticeSessionMapper;
import com.ruoyi.system.mapper.QuizQuestionMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Executable contract test for same-topic objective practice verification. */
public final class ProfilePracticeVerificationServiceContractTest
{
    public static void main(String[] args) throws Exception
    {
        FakePracticeMapper practices = new FakePracticeMapper();
        FakeQuestionMapper questions = new FakeQuestionMapper();
        RecordingProfileTool profileTool = new RecordingProfileTool();
        for (long id = 1; id <= 5; id++) {
            practices.items.add(item(id, id != 5, false));
            questions.questions.add(question(id, "single_choice", "[\"二叉树\"]"));
        }
        practices.items.add(item(6L, null, true));
        questions.questions.add(question(6L, "short_answer", "[\"二叉树\"]"));

        ProfilePracticeVerificationService service = new ProfilePracticeVerificationService();
        inject(service, "practiceSessionMapper", practices);
        inject(service, "questionMapper", questions);
        inject(service, "profileTool", profileTool);
        service.verifyCompletedSession(7L, "session-1");

        require(profileTool.calls == 1, "eligible practice did not create one verification");
        require("二叉树".equals(profileTool.topic), "verification used the wrong topic");
        require(profileTool.sampleCount == 5, "pending subjective answer was counted");
        require(profileTool.correctCount == 4, "objective correct count was wrong");

        practices.items.add(item(7L, true, false));
        questions.questions.add(question(7L, "single_choice", "[\"链表\"]"));
        service.verifyCompletedSession(7L, "mixed-session");
        require(profileTool.calls == 1, "mixed-topic practice was incorrectly verified");
    }

    private static QuizPracticeItem item(Long id, Boolean correct, boolean pending)
    {
        QuizPracticeItem item = new QuizPracticeItem();
        item.setQuestionId(id);
        item.setState("SUBMITTED");
        item.setIsCorrect(correct);
        item.setPendingReview(pending);
        return item;
    }

    private static QuizQuestion question(Long id, String type, String knowledgePoints)
    {
        QuizQuestion question = new QuizQuestion();
        question.setId(id);
        question.setQuestionType(type);
        question.setKnowledgePoints(knowledgePoints);
        return question;
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingProfileTool extends ProfileTool
    {
        private int calls;
        private String topic;
        private int sampleCount;
        private int correctCount;
        @Override public ProfileUpdateResult applyVerifiedMastery(Long userId, String topic, int sampleCount,
                                                                 int correctCount, String sessionId)
        {
            calls++;
            this.topic = topic;
            this.sampleCount = sampleCount;
            this.correctCount = correctCount;
            return new ProfileUpdateResult("{}", true, 1, Set.of("knowledgeMastery"), "ok");
        }
    }

    private static final class FakePracticeMapper implements QuizPracticeSessionMapper
    {
        private final List<QuizPracticeItem> items = new ArrayList<>();
        @Override public List<QuizPracticeItem> selectItemsBySessionIdAndUserId(String sessionId, Long userId) { return items; }
        @Override public int insertSession(QuizPracticeSession value) { return 0; }
        @Override public int insertItem(QuizPracticeItem value) { return 0; }
        @Override public QuizPracticeSession selectSessionByIdAndUserId(String id, Long userId) { return null; }
        @Override public QuizPracticeSession selectSessionForUpdateByIdAndUserId(String id, Long userId) { return null; }
        @Override public QuizPracticeSession selectActiveSessionByUserId(Long userId) { return null; }
        @Override public QuizPracticeItem selectItemForUpdate(String id, Long userId, Long questionId) { return null; }
        @Override public int updateItemDraft(QuizPracticeItem value) { return 0; }
        @Override public int markItemSubmitted(QuizPracticeItem value) { return 0; }
        @Override public int refreshSessionCounts(String id, Long userId) { return 0; }
        @Override public int updateCurrentPosition(String id, Long userId, Integer position) { return 0; }
        @Override public int finishSession(String id, Long userId, String status) { return 0; }
    }

    private static final class FakeQuestionMapper implements QuizQuestionMapper
    {
        private final List<QuizQuestion> questions = new ArrayList<>();
        @Override public QuizQuestion selectQuestionById(Long id) { return questions.stream().filter(q -> id.equals(q.getId())).findFirst().orElse(null); }
        @Override public int insertQuestion(QuizQuestion value) { return 0; }
        @Override public List<QuizQuestion> selectByDifficulty(String difficulty, int limit) { return List.of(); }
        @Override public int updateCorrectRate(Long id, java.math.BigDecimal rate) { return 0; }
        @Override public List<StudentQuizQuestion> selectStudentQuestions(QuizQuestionQuery query) { return List.of(); }
        @Override public long countStudentQuestions(QuizQuestionQuery query) { return 0; }
        @Override public StudentQuizQuestion selectStudentQuestionById(Long id) { return null; }
        @Override public StudentQuizQuestion selectStudentQuestionByIdIncludingDisabled(Long id) { return null; }
        @Override public List<String> selectStudentQuestionTypes() { return List.of(); }
        @Override public List<String> selectStudentDifficulties() { return List.of(); }
        @Override public List<String> selectStudentKnowledgePointJsons() { return List.of(); }
    }
}
