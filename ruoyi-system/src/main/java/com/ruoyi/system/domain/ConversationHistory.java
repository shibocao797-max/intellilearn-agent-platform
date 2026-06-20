package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 对话历史表 conversation_history 实体
 *
 * 用于全量持久化用户与 AI 的对话记录，每条消息（user/assistant #) 各存一条。
 * 隔离策略：所有查询必须带 user_id 条件，防止跨用户数据泄露。
 *
 * @author edu-agent
 */
public class ConversationHistory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID（关联 sys_user） */
    private Long userId;

    /** 会话ID（UUID） */
    private String sessionId;

    /** 角色: user / assistant / system */
    private String role;

    /** 消息内容（完整文本） */
    private String content;

    /** 意图类型（RESOURCE_GEN / GENERAL_CHAT 等） */
    private String intentType;

    /** 扩展元数据 JSON（实体、置信度等） */
    private String metadataJson;

    /** 创建时间（消息时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    // ===== getters/setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getIntentType() { return intentType; }
    public void setIntentType(String intentType) { this.intentType = intentType; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("sessionId", getSessionId())
            .append("role", getRole())
            .append("content", getContent())
            .append("intentType", getIntentType())
            .append("createTime", getCreateTime())
            .toString();
    }
}
