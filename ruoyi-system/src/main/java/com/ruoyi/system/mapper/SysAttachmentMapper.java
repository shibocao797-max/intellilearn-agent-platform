package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.SysAttachment;

/**
 * 附件管理 数据层
 *
 * @author edu-agent
 */
public interface SysAttachmentMapper
{
    /**
     * 查询附件信息
     */
    public SysAttachment selectAttachmentById(Long id);

    /**
     * 查询附件列表
     */
    public List<SysAttachment> selectAttachmentList(SysAttachment attachment);

    /**
     * 新增附件
     */
    public int insertAttachment(SysAttachment attachment);

    /**
     * 批量删除附件
     */
    public int deleteAttachmentByIds(Long[] ids);
}
