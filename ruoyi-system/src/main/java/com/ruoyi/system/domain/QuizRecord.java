package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 答题记录表 quiz_record 实体
 *
 * @author edu-agent
 */
public class QuizRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long questionId;
    private String sessionId;
    private String userAnswer;
    private Boolean isCorrect;
    private BigDecimal score;
    private Integer timeSpentSec;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date answerTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getTimeSpentSec() { return timeSpentSec; }
    public void setTimeSpentSec(Integer timeSpentSec) { this.timeSpentSec = timeSpentSec; }
    public Date getAnswerTime() { return answerTime; }
    public void setAnswerTime(Date answerTime) { this.answerTime = answerTime; }
    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId()).append("userId", getUserId())
            .append("questionId", getQuestionId()).append("isCorrect", getIsCorrect())
            .toString();
    }
}
