package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.system.domain.SysAttachment;
import com.ruoyi.system.mapper.SysAttachmentMapper;
import com.ruoyi.system.service.ISysAttachmentService;

/**
 * 附件管理 服务实现 —— 删除数据库记录时同步删除物理文件
 *
 * @author edu-agent
 */
@Service
public class SysAttachmentServiceImpl implements ISysAttachmentService
{
    @Autowired
    private SysAttachmentMapper attachmentMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    public SysAttachment selectAttachmentById(Long id)
    {
        return attachmentMapper.selectAttachmentById(id);
    }

    @Override
    public List<SysAttachment> selectAttachmentList(SysAttachment attachment)
    {
        return attachmentMapper.selectAttachmentList(attachment);
    }

    @Override
    public int insertAttachment(SysAttachment attachment)
    {
        return attachmentMapper.insertAttachment(attachment);
    }

    @Override
    public int deleteAttachmentByIds(Long[] ids)
    {
        // 先查询文件路径，删除物理文件
        for (Long id : ids)
        {
            SysAttachment attachment = attachmentMapper.selectAttachmentById(id);
            if (attachment != null)
            {
                fileStorageService.delete(attachment.getFilePath());
            }
        }
        // 再删除数据库记录
        return attachmentMapper.deleteAttachmentByIds(ids);
    }
}
