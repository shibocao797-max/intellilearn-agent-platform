package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 学习者画像表 learner_profile 实体
 *
 * 存储动态学习画像，包含 ≥6 个维度的 profile_json。
 * user_id 有唯一索引，首次插入 / 后续全量覆盖更新。
 *
 * @author edu-agent
 */
public class LearnerProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID（关联 sys_user，唯一） */
    private Long userId;

    /** 完整画像JSON（≥6维度） */
    private String profileJson;

    /** 画像版本号（每次更新+1） */
    private Integer version;

    /** 已抽取维度数量（目标≥6） */
    private Integer dimensionCount;

    /** 最近一次画像抽取时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastExtractAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ===== getters/setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getProfileJson() { return profileJson; }
    public void setProfileJson(String profileJson) { this.profileJson = profileJson; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getDimensionCount() { return dimensionCount; }
    public void setDimensionCount(Integer dimensionCount) { this.dimensionCount = dimensionCount; }

    public Date getLastExtractAt() { return lastExtractAt; }
    public void setLastExtractAt(Date lastExtractAt) { this.lastExtractAt = lastExtractAt; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public Date getUpdateTime() { return updateTime; }
    @Override
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("version", getVersion())
            .append("dimensionCount", getDimensionCount())
            .append("lastExtractAt", getLastExtractAt())
            .toString();
    }
}
