package com.ruoyi.web.controller.system;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.QuizQuestion;
import com.ruoyi.system.domain.QuizRecord;
import com.ruoyi.system.mapper.QuizQuestionMapper;
import com.ruoyi.system.mapper.QuizRecordMapper;
import com.ruoyi.system.service.IEduQuizPracticeService;
import com.ruoyi.system.ai.service.ConversationService;
import dev.langchain4j.model.chat.ChatModel;

import java.math.BigDecimal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户端题库 Controller — 练习/考试/提交/记录
 *
 * @author edu-agent
 */
@RestController
@RequestMapping("/edu/quiz")
public class EduQuizController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(EduQuizController.class);
    private static final int MAX_TIME_SPENT_SECONDS = 8 * 60 * 60;
    private static final Set<String> OBJECTIVE_QUESTION_TYPES = Set.of(
        "single_choice", "multi_choice", "fill_blank");

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired
    private IEduQuizPracticeService quizPracticeService;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired
    private ConversationService conversationService;

    /**
     * 按 ID 列表批量获取题目
     */
    @GetMapping("/user/list")
    public AjaxResult userList(@RequestParam(required = false) String ids) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            for (String idStr : ids.split(",")) {
                try {
                    QuizQuestion q = quizQuestionMapper.selectQuestionById(Long.parseLong(idStr.trim()));
                    if (q != null && "0".equals(q.getStatus())) questions.add(q);
                } catch (NumberFormatException ignored) {}
            }
        }
        // 无 ids 则返回最近 20 道题
        if (questions.isEmpty() && (ids == null || ids.isEmpty())) {
            questions = quizQuestionMapper.selectByDifficulty(null, 20);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizQuestion question : questions) {
            result.add(toUserQuestion(question));
        }
        return success(result);
    }

    /** 单题详情 */
    @GetMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        QuizQuestion q = quizQuestionMapper.selectQuestionById(id);
        if (q != null && "0".equals(q.getStatus())) {
            return success(toUserQuestion(q));
        }
        return error("题目不存在");
    }

    /** 提交答案并批改 */
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getUserId();
        if (body == null || body.get("questionId") == null) {
            return error("题目不能为空");
        }

        Long questionId;
        try {
            questionId = Long.valueOf(body.get("questionId").toString());
        } catch (NumberFormatException e) {
            return error("题目编号无效");
        }
        String userAnswer = (String) body.getOrDefault("userAnswer", "");
        String sessionId = (String) body.getOrDefault("sessionId", "");
        Integer timeSpent;
        try {
            timeSpent = parseTimeSpent(body.get("timeSpent"));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        QuizQuestion question = quizQuestionMapper.selectQuestionById(questionId);
        if (question == null || !"0".equals(question.getStatus())) return error("题目不存在");

        // 批改：比较标准答案（忽略空格大小写）
        String correctAnswer = question.getAnswer();
        boolean pendingReview = !OBJECTIVE_QUESTION_TYPES.contains(question.getQuestionType());
        Boolean isCorrect = pendingReview ? null
            : cleanAnswer(userAnswer).equalsIgnoreCase(cleanAnswer(correctAnswer));

        // 写入答题记录
        QuizRecord record = new QuizRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setSessionId(sessionId);
        record.setUserAnswer(userAnswer);
        record.setIsCorrect(isCorrect);
        record.setScore(isCorrect == null ? null
            : (isCorrect ? new BigDecimal("100") : BigDecimal.ZERO));
        record.setTimeSpentSec(timeSpent);
        record.setAnswerTime(new Date());
        quizRecordMapper.insertRecord(record);

        // 记录答题行为（静默记录，失败不影响主流程）
        try {
            if (behaviorTool != null) {
                behaviorTool.logBehavior(userId, "ANSWER_QUIZ", "quiz",
                        questionId, sessionId, timeSpent);
            }
        } catch (Exception ignore) { /* 行为记录失败不阻塞 */ }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isCorrect", isCorrect);
        result.put("pendingReview", pendingReview);
        result.put("correctAnswer", correctAnswer);
        result.put("explanation", question.getExplanation());
        result.put("questionType", question.getQuestionType());
        return success(result);
    }

    /** 答题记录 */
    @GetMapping("/records")
    public AjaxResult records(@RequestParam(defaultValue = "50") int limit) {
        return success(quizRecordMapper.selectByUser(SecurityUtils.getUserId(), limit));
    }

    /** 答题统计 */
    @GetMapping("/stats")
    public AjaxResult stats() {
        return success(quizRecordMapper.selectStats(SecurityUtils.getUserId()));
    }

    // ================================================================
    // AI 引导式讲解
    // ================================================================

    /** 学习行为追踪工具 — 记录答题行为 */
    @Autowired(required = false)
    private com.ruoyi.system.ai.tool.BehaviorTool behaviorTool;

    /** AI 引导式讲解 */
    @PostMapping("/ai-explain")
    public AjaxResult aiExplain(@RequestBody Map<String, Object> body) {
        Long questionId = Long.valueOf(body.get("questionId").toString());
        String userExplanation = (String) body.getOrDefault("userExplanation", "");
        String practiceSessionId = (String) body.getOrDefault("sessionId", "");

        QuizQuestion q = quizQuestionMapper.selectQuestionById(questionId);
        Long userId = SecurityUtils.getUserId();
        if (q == null) {
            return error("Question not found");
        }
        if (practiceSessionId == null || practiceSessionId.isBlank()) {
            return error("请在练习会话中提交本题答案后再请求讲解");
        }
        quizPracticeService.requireSubmittedQuestion(userId, practiceSessionId, questionId);
        String prompt = String.format("""
            教学引导式讲解，先问学生一个思考问题，再给提示，最后才给出完整解答（不要在第一句就直接给出答案）。
            题干: %s
            正确选项/答案: %s
            """, q.getStem(), q.getAnswer());

        String explanation = chatModel != null ? chatModel.chat(prompt) : "AI 服务暂不可用";
        return success(Map.of("explanation", explanation));
    }

    private String cleanAnswer(String s) {
        return s.trim().replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * Only returns fields needed before a learner submits an answer. The answer and
     * explanation remain available solely from the submit response.
     */
    private Map<String, Object> toUserQuestion(QuizQuestion question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", question.getId());
        result.put("questionType", question.getQuestionType());
        result.put("stem", question.getStem());
        result.put("options", question.getOptions());
        result.put("difficulty", question.getDifficulty());
        result.put("knowledgePoints", question.getKnowledgePoints());
        return result;
    }

    /**
     * 客户端按秒上报用时；允许缺省值以兼容旧客户端，但拒绝负数、非整数和异常长的记录。
     */
    private int parseTimeSpent(Object rawTimeSpent) {
        if (rawTimeSpent == null) {
            return 0;
        }

        final int seconds;
        try {
            seconds = new BigDecimal(rawTimeSpent.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("答题用时必须为整数秒");
        }

        if (seconds < 0 || seconds > MAX_TIME_SPENT_SECONDS) {
            throw new IllegalArgumentException("答题用时必须介于 0 和 " + MAX_TIME_SPENT_SECONDS + " 秒之间");
        }
        return seconds;
    }
}
