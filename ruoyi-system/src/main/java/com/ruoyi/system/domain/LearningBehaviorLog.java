package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 学习行为日志表 learning_behavior_log 实体
 *
 * 记录用户的每一次学习行为（查看资源、提交答题、完成节点等），
 * 为学习报告、行为分析、学习效果评估提供数据基础。
 *
 * @author edu-agent
 */
public class LearningBehaviorLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String sessionId;
    private Integer durationSec;
    private String metadataJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    // ---- 构造器 ----
    public LearningBehaviorLog() {}

    /**
     * 快捷构造 — 最常用的行为记录场景
     *
     * @param userId      用户 ID
     * @param actionType  行为类型（VIEW_RESOURCE / ANSWER_QUIZ / COMPLETE_NODE / CONVERSATION 等）
     * @param targetType  目标类型（resource / quiz / path_node / agent）
     * @param targetId    目标 ID
     * @param sessionId   会话 ID（可为 null）
     */
    public LearningBehaviorLog(Long userId, String actionType, String targetType,
                               Long targetId, String sessionId) {
        this.userId = userId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.sessionId = sessionId;
        this.createTime = new Date();
    }

    // ---- getters/setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId()).append("userId", getUserId())
            .append("actionType", getActionType()).append("targetId", getTargetId())
            .toString();
    }
}
