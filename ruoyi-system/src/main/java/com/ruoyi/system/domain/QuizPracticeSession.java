package com.ruoyi.system.domain;

import java.util.Date;

/** Persistent learner practice session. */
public class QuizPracticeSession
{
    private String id;
    private Long userId;
    private String source;
    private String status;
    private Integer totalCount;
    private Integer submittedCount;
    private Integer correctCount;
    private Integer currentPosition;
    private Date startedAt;
    private Date lastActiveAt;
    private Date finishedAt;
    private Date createTime;
    private Date updateTime;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount)
    {
        this.totalCount = totalCount;
    }

    public Integer getSubmittedCount()
    {
        return submittedCount;
    }

    public void setSubmittedCount(Integer submittedCount)
    {
        this.submittedCount = submittedCount;
    }

    public Integer getCorrectCount()
    {
        return correctCount;
    }

    public void setCorrectCount(Integer correctCount)
    {
        this.correctCount = correctCount;
    }

    public Integer getCurrentPosition()
    {
        return currentPosition;
    }

    public void setCurrentPosition(Integer currentPosition)
    {
        this.currentPosition = currentPosition;
    }

    public Date getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(Date startedAt)
    {
        this.startedAt = startedAt;
    }

    public Date getLastActiveAt()
    {
        return lastActiveAt;
    }

    public void setLastActiveAt(Date lastActiveAt)
    {
        this.lastActiveAt = lastActiveAt;
    }

    public Date getFinishedAt()
    {
        return finishedAt;
    }

    public void setFinishedAt(Date finishedAt)
    {
        this.finishedAt = finishedAt;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
