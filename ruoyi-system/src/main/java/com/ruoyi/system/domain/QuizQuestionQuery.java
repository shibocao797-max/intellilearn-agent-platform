package com.ruoyi.system.domain;

/**
 * Whitelisted filters accepted by the learner question bank and wrong-answer
 * endpoints. Offset is computed by the service, never accepted from clients.
 */
public class QuizQuestionQuery
{
    private int pageNum = 1;
    private int pageSize = 20;
    private int offset;
    private String keyword;
    private String questionType;
    private String difficulty;
    private String knowledgePoint;

    public int getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(int pageNum)
    {
        this.pageNum = pageNum;
    }

    public int getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(int pageSize)
    {
        this.pageSize = pageSize;
    }

    public int getOffset()
    {
        return offset;
    }

    public void setOffset(int offset)
    {
        this.offset = offset;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public String getQuestionType()
    {
        return questionType;
    }

    public void setQuestionType(String questionType)
    {
        this.questionType = questionType;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(String difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getKnowledgePoint()
    {
        return knowledgePoint;
    }

    public void setKnowledgePoint(String knowledgePoint)
    {
        this.knowledgePoint = knowledgePoint;
    }
}
