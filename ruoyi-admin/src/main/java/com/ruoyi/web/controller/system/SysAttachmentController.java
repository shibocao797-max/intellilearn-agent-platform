package com.ruoyi.web.controller.system;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.SysAttachment;
import com.ruoyi.system.service.ISysAttachmentService;

/**
 * 附件管理
 *
 * @author edu-agent
 */
@RestController
@RequestMapping("/system/attachment")
public class SysAttachmentController extends BaseController
{
    @Autowired
    private ISysAttachmentService attachmentService;

    /**
     * 获取附件列表
     */
    @PreAuthorize("@ss.hasPermi('system:attachment:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysAttachment attachment)
    {
        startPage();
        List<SysAttachment> list = attachmentService.selectAttachmentList(attachment);
        return getDataTable(list);
    }

    /**
     * 获取附件详情
     */
    @PreAuthorize("@ss.hasPermi('system:attachment:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(attachmentService.selectAttachmentById(id));
    }

    /**
     * 删除附件（同时删除物理文件）
     */
    @PreAuthorize("@ss.hasPermi('system:attachment:list')")
    @Log(title = "附件管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        attachmentService.deleteAttachmentByIds(ids);
        return success();
    }
}
