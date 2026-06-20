package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.SysAttachment;

/**
 * 附件管理 服务层
 *
 * @author edu-agent
 */
public interface ISysAttachmentService
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
     * 批量删除附件（同时删除物理文件）
     */
    public int deleteAttachmentByIds(Long[] ids);
}
