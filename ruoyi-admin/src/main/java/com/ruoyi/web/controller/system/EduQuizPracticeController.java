package com.ruoyi.web.controller.system;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.QuizQuestionQuery;
import com.ruoyi.system.service.IEduQuizPracticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Learner-facing question bank, wrong-answer and resumable practice APIs. */
@RestController
@RequestMapping("/edu/quiz")
public class EduQuizPracticeController extends BaseController
{
    @Autowired
    private IEduQuizPracticeService quizPracticeService;

    @GetMapping("/questions")
    public AjaxResult questions(
        @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "questionType", required = false) String questionType,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "difficulty", required = false) String difficulty,
        @RequestParam(value = "knowledgePoint", required = false) String knowledgePoint)
    {
        return success(quizPracticeService.questionBank(query(
            page == null ? pageNum : page, pageSize, keyword,
            questionType == null ? type : questionType, difficulty, knowledgePoint)));
    }

    @GetMapping("/question-facets")
    public AjaxResult questionFacets()
    {
        return success(quizPracticeService.questionFacets());
    }

    @GetMapping("/wrong-questions")
    public AjaxResult wrongQuestions(
        @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "questionType", required = false) String questionType,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "difficulty", required = false) String difficulty,
        @RequestParam(value = "knowledgePoint", required = false) String knowledgePoint)
    {
        return success(quizPracticeService.wrongQuestions(SecurityUtils.getUserId(), query(
            page == null ? pageNum : page, pageSize, keyword,
            questionType == null ? type : questionType, difficulty, knowledgePoint)));
    }

    @PostMapping("/sessions")
    public AjaxResult createSession(@RequestBody(required = false) Map<String, Object> body)
    {
        Map<String, Object> request = body == null ? Map.of() : body;
        return success(quizPracticeService.createSession(SecurityUtils.getUserId(),
            questionIds(request.get("questionIds")), stringValue(request.get("source"))));
    }

    @GetMapping("/sessions/active")
    public AjaxResult activeSession()
    {
        return success(quizPracticeService.activeSession(SecurityUtils.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}")
    public AjaxResult sessionDetail(@PathVariable String sessionId)
    {
        return success(quizPracticeService.sessionDetail(SecurityUtils.getUserId(), sessionId));
    }

    @PatchMapping("/sessions/{sessionId}/items/{questionId}")
    public AjaxResult saveDraft(@PathVariable String sessionId, @PathVariable Long questionId,
                                @RequestBody(required = false) Map<String, Object> body)
    {
        Map<String, Object> request = body == null ? Map.of() : body;
        return success(quizPracticeService.saveDraft(SecurityUtils.getUserId(), sessionId, questionId,
            request.get("draftAnswer"), nullableBoolean(request.get("flagged")),
            nullableInteger(request.get("currentPosition"))));
    }

    @PostMapping("/sessions/{sessionId}/items/{questionId}/submit")
    public AjaxResult submit(@PathVariable String sessionId, @PathVariable Long questionId,
                             @RequestBody(required = false) Map<String, Object> body)
    {
        Map<String, Object> request = body == null ? Map.of() : body;
        return success(quizPracticeService.submit(SecurityUtils.getUserId(), sessionId, questionId,
            request.get("userAnswer"), request.get("timeSpent"), stringValue(request.get("idempotencyKey"))));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    public AjaxResult finish(@PathVariable String sessionId, @RequestBody(required = false) Map<String, Object> body)
    {
        Map<String, Object> request = body == null ? Map.of() : body;
        return success(quizPracticeService.finish(SecurityUtils.getUserId(), sessionId,
            Boolean.TRUE.equals(nullableBoolean(request.get("abandon")))));
    }

    @GetMapping("/sessions/{sessionId}/result")
    public AjaxResult result(@PathVariable String sessionId)
    {
        return success(quizPracticeService.sessionResult(SecurityUtils.getUserId(), sessionId));
    }

    private QuizQuestionQuery query(int pageNum, int pageSize, String keyword,
                                    String questionType, String difficulty, String knowledgePoint)
    {
        QuizQuestionQuery query = new QuizQuestionQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setQuestionType(questionType);
        query.setDifficulty(difficulty);
        query.setKnowledgePoint(knowledgePoint);
        return query;
    }

    private List<Long> questionIds(Object value)
    {
        List<Long> ids = new ArrayList<>();
        if (value instanceof Collection<?> values)
        {
            for (Object item : values)
            {
                ids.add(longValue(item));
            }
            return ids;
        }
        if (value != null)
        {
            for (String item : String.valueOf(value).split(","))
            {
                if (!item.isBlank())
                {
                    ids.add(longValue(item));
                }
            }
        }
        return ids;
    }

    private Long longValue(Object value)
    {
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("Question id is invalid");
        }
    }

    private String stringValue(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean nullableBoolean(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Boolean bool)
        {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text))
        {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text))
        {
            return Boolean.FALSE;
        }
        throw new ServiceException("Boolean value is invalid");
    }

    private Integer nullableInteger(Object value)
    {
        if (value == null || String.valueOf(value).isBlank())
        {
            return null;
        }
        try
        {
            return Integer.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("Number value is invalid");
        }
    }
}
