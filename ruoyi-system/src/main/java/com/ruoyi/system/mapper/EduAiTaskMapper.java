package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.EduAiTask;

public interface EduAiTaskMapper
{
    int insertOrIgnore(EduAiTask task);
    EduAiTask selectByTaskId(String taskId);
    EduAiTask selectByTaskIdForUpdate(String taskId);
    EduAiTask selectOwned(String taskId, Long userId);
    EduAiTask selectByUserAndClientRequestId(Long userId, String clientRequestId);
    EduAiTask selectByUserAndClientRequestIdForUpdate(Long userId, String clientRequestId);
    List<EduAiTask> selectActive(Long userId, String sessionId);
    List<EduAiTask> selectInterrupted();
    int markRunning(String taskId);
    int requestCancel(String taskId, Long userId);
    Boolean selectCancelRequested(String taskId);
    int markQueuedCancelled(String taskId, Long userId);
    int markTerminal(String taskId, String status, String resultJson, String errorMessage);
}
