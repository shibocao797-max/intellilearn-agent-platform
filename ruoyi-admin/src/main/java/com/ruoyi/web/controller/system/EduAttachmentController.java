package com.ruoyi.web.controller.system;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.IEduChatAttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import com.ruoyi.common.utils.file.FileStorageService;
import java.util.Map;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;

@RestController
@RequestMapping("/edu/ai/attachments")
public class EduAttachmentController extends BaseController {
    @Autowired private IEduChatAttachmentService attachmentService;
    @Autowired private FileStorageService fileStorageService;
    @PostMapping
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    public AjaxResult upload(@RequestParam("sessionId") String sessionId, @RequestParam("file") MultipartFile file) {
        var attachment = attachmentService.upload(SecurityUtils.getUserId(), sessionId, file);
        return success(attachmentService.publicMetadata(attachment));
    }
    @GetMapping("/{attachmentId}")
    public AjaxResult get(@PathVariable Long attachmentId) { return success(attachmentService.publicMetadata(attachmentService.getOwned(SecurityUtils.getUserId(), attachmentId))); }
    @GetMapping("/{attachmentId}/download")
    public void download(@PathVariable Long attachmentId, HttpServletResponse response) throws Exception {
        // Ownership is checked before the server-only storage path is passed to the storage adapter.
        fileStorageService.download(attachmentService.getOwned(SecurityUtils.getUserId(), attachmentId).getStoragePath(), response);
    }
    @DeleteMapping("/{attachmentId}")
    public AjaxResult delete(@PathVariable Long attachmentId) { attachmentService.delete(SecurityUtils.getUserId(), attachmentId); return success(Map.of("attachmentId", attachmentId)); }
}
