package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.QuizQuestion;
import com.ruoyi.system.domain.QuizQuestionQuery;
import com.ruoyi.system.domain.StudentQuizQuestion;

/**
 * 题库表 Mapper 接口
 *
 * 支持：插入题目、按知识点/难度查询、批量插入。
 *
 * @author edu-agent
 */
public interface QuizQuestionMapper
{
    /**
     * 插入一道题目
     *
     * @param question 题目实体
     * @return 插入行数
     */
    public int insertQuestion(QuizQuestion question);

    /**
     * 根据知识点和难度查询题目
     *
     * @param difficulty 难度（null=不限）
     * @param limit      最大条数
     * @return 题目列表
     */
    public List<QuizQuestion> selectByDifficulty(String difficulty, int limit);

    /**
     * 根据 ID 查询题目
     */
    public QuizQuestion selectQuestionById(Long id);

    /**
     * 更新题目正确率（答题后动态调整）
     */
    public int updateCorrectRate(Long id, java.math.BigDecimal correctRate);

    /** Learner-safe, paged question-bank query. */
    List<StudentQuizQuestion> selectStudentQuestions(@Param("query") QuizQuestionQuery query);

    long countStudentQuestions(@Param("query") QuizQuestionQuery query);

    StudentQuizQuestion selectStudentQuestionById(Long id);

    /** Used only when restoring an already-created learner session. */
    StudentQuizQuestion selectStudentQuestionByIdIncludingDisabled(Long id);

    List<String> selectStudentQuestionTypes();

    List<String> selectStudentDifficulties();

    List<String> selectStudentKnowledgePointJsons();
}
