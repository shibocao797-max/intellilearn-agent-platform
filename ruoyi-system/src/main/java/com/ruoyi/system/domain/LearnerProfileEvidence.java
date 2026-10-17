package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;

/** Source record supporting a profile conclusion. */
public class LearnerProfileEvidence {
    private Long id;
    private Long userId;
    private Long profileId;
    private Integer profileVersion;
    private String dimensionKey;
    private String sourceType;
    private String sourceRef;
    private String excerpt;
    private BigDecimal confidence;
    private String evidenceStatus;
    private Date createTime;

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
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getEvidenceStatus() { return evidenceStatus; }
    public void setEvidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
