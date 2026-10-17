package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 教育知识库切片表 edu_knowledge_chunk
 *
 * @author edu-agent
 */
public class EduKnowledgeChunk extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 知识标题 */
    private String title;

    /** 学科 */
    private String subject;

    /** 适用层次 */
    private String gradeLevel;

    /** 来源类型: textbook/official_doc/course_note/manual */
    private String sourceType;

    /** 来源名称 */
    private String sourceName;

    /** 来源链接 */
    private String sourceUrl;

    /** 切片正文 */
    private String chunkText;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 标签 */
    private String tags;

    /** 可信等级: high/medium/low */
    private String trustLevel;

    /** 内容哈希 */
    private String contentHash;

    /** 状态: 0=启用 1=停用 */
    private String enabled;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSubject()
    {
        return subject;
    }

    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    public String getGradeLevel()
    {
        return gradeLevel;
    }

    public void setGradeLevel(String gradeLevel)
    {
        this.gradeLevel = gradeLevel;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public void setSourceName(String sourceName)
    {
        this.sourceName = sourceName;
    }

    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    public String getChunkText()
    {
        return chunkText;
    }

    public void setChunkText(String chunkText)
    {
        this.chunkText = chunkText;
    }

    public Integer getChunkIndex()
    {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex)
    {
        this.chunkIndex = chunkIndex;
    }

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public String getTrustLevel()
    {
        return trustLevel;
    }

    public void setTrustLevel(String trustLevel)
    {
        this.trustLevel = trustLevel;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("title", getTitle())
                .append("subject", getSubject())
                .append("gradeLevel", getGradeLevel())
                .append("sourceType", getSourceType())
                .append("sourceName", getSourceName())
                .append("sourceUrl", getSourceUrl())
                .append("chunkIndex", getChunkIndex())
                .append("tags", getTags())
                .append("trustLevel", getTrustLevel())
                .append("contentHash", getContentHash())
                .append("enabled", getEnabled())
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .toString();
    }
}
