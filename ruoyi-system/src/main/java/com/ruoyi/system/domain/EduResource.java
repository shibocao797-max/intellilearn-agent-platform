package com.ruoyi.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 推荐资源表 edu_resource
 *
 * @author ruoyi
 */
public class EduResource extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 资源标题 */
    private String title;

    /** 资源简介 */
    private String description;

    /** 封面图片URL */
    private String coverUrl;

    /** 资源类型: video/image/article/code/mindmap */
    private String type;

    /** 来源: internal/bilibili/youtube/other */
    private String source;

    /** 外部链接地址 */
    private String sourceUrl;

    /** B站BV号 */
    private String bvid;

    /** 时长/阅读时间描述 */
    private String duration;

    /** 标签 */
    private String tag;

    /** 卡片图标emoji */
    private String icon;

    /** 卡片头部背景色 */
    private String headerColor;

    /** 排序权重(越大越靠前) */
    private Integer sortOrder;

    /** 状态: 0=发布 1=下架 */
    private String status;

    /** 用户ID（NULL=管理端推荐资源，非NULL=某个用户的AI生成资源） */
    private Long userId;

    /** 资源正文内容（富文本/代码/markdown，非视频类型使用） */
    private String content;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @NotBlank(message = "资源标题不能为空")
    @Size(min = 0, max = 200, message = "资源标题不能超过200个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    @Size(min = 0, max = 500, message = "资源简介不能超过500个字符")
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    public String getBvid()
    {
        return bvid;
    }

    public void setBvid(String bvid)
    {
        this.bvid = bvid;
    }

    public String getDuration()
    {
        return duration;
    }

    public void setDuration(String duration)
    {
        this.duration = duration;
    }

    public String getTag()
    {
        return tag;
    }

    public void setTag(String tag)
    {
        this.tag = tag;
    }

    public String getIcon()
    {
        return icon;
    }

    public void setIcon(String icon)
    {
        this.icon = icon;
    }

    public String getHeaderColor()
    {
        return headerColor;
    }

    public void setHeaderColor(String headerColor)
    {
        this.headerColor = headerColor;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("title", getTitle())
            .append("description", getDescription())
            .append("coverUrl", getCoverUrl())
            .append("type", getType())
            .append("source", getSource())
            .append("sourceUrl", getSourceUrl())
            .append("bvid", getBvid())
            .append("duration", getDuration())
            .append("tag", getTag())
            .append("icon", getIcon())
            .append("headerColor", getHeaderColor())
            .append("sortOrder", getSortOrder())
            .append("status", getStatus())
            .append("userId", getUserId())
            .append("content", getContent())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
