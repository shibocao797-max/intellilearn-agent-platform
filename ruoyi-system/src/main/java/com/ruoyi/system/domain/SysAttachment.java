package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 附件管理表 sys_attachment
 *
 * @author edu-agent
 */
public class SysAttachment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 附件ID */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 存储路径/URL */
    private String filePath;

    /** 文件大小(字节) */
    private Long fileSize;

    /** MIME类型 */
    private String mimeType;

    /** 文件扩展名 */
    private String extension;

    /** 关联实体类型 */
    private String entityType;

    /** 关联实体ID */
    private Long entityId;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getMimeType()
    {
        return mimeType;
    }

    public void setMimeType(String mimeType)
    {
        this.mimeType = mimeType;
    }

    public String getExtension()
    {
        return extension;
    }

    public void setExtension(String extension)
    {
        this.extension = extension;
    }

    public String getEntityType()
    {
        return entityType;
    }

    public void setEntityType(String entityType)
    {
        this.entityType = entityType;
    }

    public Long getEntityId()
    {
        return entityId;
    }

    public void setEntityId(Long entityId)
    {
        this.entityId = entityId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("fileName", getFileName())
            .append("filePath", getFilePath())
            .append("fileSize", getFileSize())
            .append("mimeType", getMimeType())
            .append("extension", getExtension())
            .append("entityType", getEntityType())
            .append("entityId", getEntityId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
