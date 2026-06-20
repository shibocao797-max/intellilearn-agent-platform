package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.QuizQuestion;

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
}
