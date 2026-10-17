package com.ruoyi.system.domain;

/**
 * The learner-safe projection of a quiz question. It intentionally excludes
 * the answer and explanation so it can be used before an answer is submitted.
 */
public class StudentQuizQuestion
{
    private Long id;
    private String questionType;
    private String stem;
    private String options;
    private String difficulty;
    private String knowledgePoints;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getQuestionType()
    {
        return questionType;
    }

    public void setQuestionType(String questionType)
    {
        this.questionType = questionType;
    }

    public String getStem()
    {
        return stem;
    }

    public void setStem(String stem)
    {
        this.stem = stem;
    }

    public String getOptions()
    {
        return options;
    }

    public void setOptions(String options)
    {
        this.options = options;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(String difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getKnowledgePoints()
    {
        return knowledgePoints;
    }

    public void setKnowledgePoints(String knowledgePoints)
    {
        this.knowledgePoints = knowledgePoints;
    }
}
