package com.ruoyi.system.domain;

import java.util.Date;

/** User resolution for one dimension in one learner-profile version. */
public class LearnerProfileConfirmation
{
    private Long id;
    private Long userId;
    private Long profileId;
    private Integer profileVersion;
    private String dimensionKey;
    private String status;
    private String correctionValueJson;
    private String rejectReason;
    private Long confirmedBy;
    private Date confirmedAt;
    private Date createTime;
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Integer getProfileVersion() { return profileVersion; }
    public void setProfileVersion(Integer profileVersion) { this.profileVersion = profileVersion; }
    public String getDimensionKey() { return dimensionKey; }
    public void setDimensionKey(String dimensionKey) { this.dimensionKey = dimensionKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCorrectionValueJson() { return correctionValueJson; }
    public void setCorrectionValueJson(String correctionValueJson) { this.correctionValueJson = correctionValueJson; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public Date getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Date confirmedAt) { this.confirmedAt = confirmedAt; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
