package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.EduAiTask;
import com.ruoyi.system.domain.EduAiTaskEvent;

public interface IEduAiTaskService
{
    EduAiTask createTask(Long userId, String message, String sessionId,
                         List<Long> attachmentIds, String clientRequestId);
    List<EduAiTask> activeTasks(Long userId, String sessionId);
    EduAiTask getTask(Long userId, String taskId);
    List<EduAiTaskEvent> getEvents(Long userId, String taskId, Long afterSeq);
    EduAiTask cancel(Long userId, String taskId);
}
