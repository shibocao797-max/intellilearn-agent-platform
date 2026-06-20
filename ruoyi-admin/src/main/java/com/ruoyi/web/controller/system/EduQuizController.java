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

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired
    private ConversationService conversationService;

    /**
     * 按 ID 列表批量获取题目
     */
    @GetMapping("/user/list")
    public AjaxResult userList(@RequestParam(required = false) String ids) {
        List<QuizQuestion> result = new ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            for (String idStr : ids.split(",")) {
                try {
                    QuizQuestion q = quizQuestionMapper.selectQuestionById(Long.parseLong(idStr.trim()));
                    if (q != null) result.add(q);
                } catch (NumberFormatException ignored) {}
            }
        }
        // 无 ids 则返回最近 20 道题
        if (result.isEmpty() && (ids == null || ids.isEmpty())) {
            result = quizQuestionMapper.selectByDifficulty(null, 20);
        }
        return success(result);
    }

    /** 单题详情 */
    @GetMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        QuizQuestion q = quizQuestionMapper.selectQuestionById(id);
        return q != null ? success(q) : error("题目不存在");
    }

    /** 提交答案并批改 */
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getUserId();
        Long questionId = Long.valueOf(body.get("questionId").toString());
        String userAnswer = (String) body.getOrDefault("userAnswer", "");
        String sessionId = (String) body.getOrDefault("sessionId", "");
        Integer timeSpent = body.get("timeSpent") != null
                ? Integer.valueOf(body.get("timeSpent").toString()) : 0;

        QuizQuestion question = quizQuestionMapper.selectQuestionById(questionId);
        if (question == null) return error("题目不存在");

        // 批改：比较标准答案（忽略空格大小写）
        String correctAnswer = question.getAnswer();
        boolean isCorrect = cleanAnswer(userAnswer).equalsIgnoreCase(cleanAnswer(correctAnswer));

        // 写入答题记录
        QuizRecord record = new QuizRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setSessionId(sessionId);
        record.setUserAnswer(userAnswer);
        record.setIsCorrect(isCorrect);
        record.setScore(isCorrect ? new BigDecimal("100") : new BigDecimal("0"));
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

        QuizQuestion q = quizQuestionMapper.selectQuestionById(questionId);
        if (q == null) return error("题目不存在");

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
}
