package com.ruoyi.system.service;

import com.ruoyi.system.domain.EduChatAttachment;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface IEduChatAttachmentService {
    EduChatAttachment upload(Long userId, String sessionId, MultipartFile file);
    EduChatAttachment getOwned(Long userId, Long attachmentId);
    List<EduChatAttachment> validateForChat(Long userId, String sessionId, List<Long> attachmentIds);
    void delete(Long userId, Long attachmentId);
    void deleteSessionAttachments(Long userId, String sessionId);
    Map<String, Object> publicMetadata(EduChatAttachment attachment);
}
