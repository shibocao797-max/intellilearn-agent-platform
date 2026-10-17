package com.ruoyi.system.domain;

import java.util.Date;

/** Learner-safe summary of a question whose latest answer is still incorrect. */
public class QuizWrongQuestion extends StudentQuizQuestion
{
    private Long wrongCount;
    private Date lastWrongAt;

    public Long getWrongCount()
    {
        return wrongCount;
    }

    public void setWrongCount(Long wrongCount)
    {
        this.wrongCount = wrongCount;
    }

    public Date getLastWrongAt()
    {
        return lastWrongAt;
    }

    public void setLastWrongAt(Date lastWrongAt)
    {
        this.lastWrongAt = lastWrongAt;
    }
}
