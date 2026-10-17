package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;

/** Per-question mutable state within a practice session. */
public class QuizPracticeItem
{
    private Long id;
    private String sessionId;
    private Long userId;
    private Long questionId;
    private String questionSnapshotJson;
    private Integer positionIndex;
    private String state;
    private String draftAnswer;
    private Boolean flagged;
    private String submittedAnswer;
    private Boolean isCorrect;
    private Boolean pendingReview;
    private BigDecimal score;
    private Integer timeSpentSec;
    private Long quizRecordId;
    private String idempotencyKey;
    private Date submittedAt;
    private Date createTime;
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionSnapshotJson() { return questionSnapshotJson; }
    public void setQuestionSnapshotJson(String questionSnapshotJson) { this.questionSnapshotJson = questionSnapshotJson; }
    public Integer getPositionIndex() { return positionIndex; }
    public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getDraftAnswer() { return draftAnswer; }
    public void setDraftAnswer(String draftAnswer) { this.draftAnswer = draftAnswer; }
    public Boolean getFlagged() { return flagged; }
    public void setFlagged(Boolean flagged) { this.flagged = flagged; }
    public String getSubmittedAnswer() { return submittedAnswer; }
    public void setSubmittedAnswer(String submittedAnswer) { this.submittedAnswer = submittedAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean correct) { isCorrect = correct; }
    public Boolean getPendingReview() { return pendingReview; }
    public void setPendingReview(Boolean pendingReview) { this.pendingReview = pendingReview; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getTimeSpentSec() { return timeSpentSec; }
    public void setTimeSpentSec(Integer timeSpentSec) { this.timeSpentSec = timeSpentSec; }
    public Long getQuizRecordId() { return quizRecordId; }
    public void setQuizRecordId(Long quizRecordId) { this.quizRecordId = quizRecordId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
