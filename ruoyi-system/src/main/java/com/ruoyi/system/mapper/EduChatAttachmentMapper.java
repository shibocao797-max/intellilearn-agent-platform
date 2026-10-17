package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.EduChatAttachment;
import java.util.List;

public interface EduChatAttachmentMapper {
    int insert(EduChatAttachment attachment);
    EduChatAttachment selectByIdAndUserId(Long id, Long userId);
    List<EduChatAttachment> selectByIdsAndUserId(List<Long> ids, Long userId);
    List<EduChatAttachment> selectBySessionAndUserId(String sessionId, Long userId);
    int deleteByIdAndUserId(Long id, Long userId);
}
