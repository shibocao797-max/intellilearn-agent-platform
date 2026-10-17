package com.ruoyi.system.domain;

import java.util.Date;

public class EduActionPlan {
    private Long id; private Long userId; private String title; private String summary; private String status;
    private String sourceStatus; private String sourceProfileVersion; private Date createTime; private Date updateTime;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; } public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getSourceStatus() { return sourceStatus; } public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }
    public String getSourceProfileVersion() { return sourceProfileVersion; } public void setSourceProfileVersion(String sourceProfileVersion) { this.sourceProfileVersion = sourceProfileVersion; }
    public Date getCreateTime() { return createTime; } public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; } public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
