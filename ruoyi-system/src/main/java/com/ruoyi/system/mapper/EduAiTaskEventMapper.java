package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.EduAiTaskEvent;

public interface EduAiTaskEventMapper
{
    int insert(EduAiTaskEvent event);
    List<EduAiTaskEvent> selectAfter(String taskId, Long afterSeq);
}
