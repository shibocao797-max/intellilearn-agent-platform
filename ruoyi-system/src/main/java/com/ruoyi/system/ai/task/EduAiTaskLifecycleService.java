package com.ruoyi.system.ai.task;

import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import com.ruoyi.system.ai.service.ConversationService;
import com.ruoyi.system.domain.EduAiTask;
import com.ruoyi.system.domain.EduAiTaskStatus;
import com.ruoyi.system.mapper.EduAiTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns terminal transitions so assistant persistence and terminal events are ordered. */
@Service
public class EduAiTaskLifecycleService
{
    @Autowired private EduAiTaskMapper taskMapper;
    @Autowired private ConversationService conversationService;
    @Autowired private EduAiTaskEventStore eventStore;

    @Transactional(rollbackFor = Exception.class)
    public String complete(String taskId, EducationState finalState, Map<String, Object> result,
                           EduAiTaskProgressCallback callback)
    {
        EduAiTask task = requiredLockedTask(taskId);
        if (EduAiTaskStatus.isTerminal(task.getStatus()))
        {
            return task.getStatus();
        }
        if (Boolean.TRUE.equals(task.getCancelRequested()) || callback.isDisconnected())
        {
            return markCancelled(task, callback);
        }
        if (finalState == null)
        {
            return markFailed(task, "AI execution returned no final state", callback);
        }

        String textReply = UserFacingTextSanitizer.sanitize(finalState.textReply());
        if (textReply == null || textReply.isBlank())
        {
            textReply = "AI processing completed without a text response.";
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) UserFacingTextSanitizer
            .sanitizeValue(resolvedResources(finalState));
        conversationService.saveMessage(task.getUserId(), task.getSessionId(), "assistant",
            textReply, finalState.intent(), resources);

        if (taskMapper.markTerminal(taskId, EduAiTaskStatus.SUCCEEDED,
            JSON.toJSONString(result), null) != 1)
        {
            throw new ServiceException("AI task success transition was rejected");
        }
        callback.setTaskStatus(EduAiTaskStatus.SUCCEEDED);
        callback.onDone();
        return EduAiTaskStatus.SUCCEEDED;
    }

    @Transactional(rollbackFor = Exception.class)
    public String fail(String taskId, String message, EduAiTaskProgressCallback callback)
    {
        EduAiTask task = requiredLockedTask(taskId);
        if (EduAiTaskStatus.isTerminal(task.getStatus()))
        {
            return task.getStatus();
        }
        if (Boolean.TRUE.equals(task.getCancelRequested()) || callback.isDisconnected())
        {
            return markCancelled(task, callback);
        }
        return markFailed(task, sanitizeError(message), callback);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failInterrupted(String taskId)
    {
        EduAiTask task = requiredLockedTask(taskId);
        if (EduAiTaskStatus.isTerminal(task.getStatus()))
        {
            return;
        }
        String error = "Task was interrupted by an application restart";
        if (taskMapper.markTerminal(taskId, EduAiTaskStatus.FAILED, null, error) == 1)
        {
            eventStore.append(taskId, task.getSessionId(), "error", null,
                EduAiTaskStatus.FAILED, Map.of("message", error));
        }
    }

    private String markCancelled(EduAiTask task, EduAiTaskProgressCallback callback)
    {
        if (taskMapper.markTerminal(task.getTaskId(), EduAiTaskStatus.CANCELLED, null, null) != 1)
        {
            throw new ServiceException("AI task cancellation transition was rejected");
        }
        callback.setTaskStatus(EduAiTaskStatus.CANCELLED);
        callback.onDone();
        return EduAiTaskStatus.CANCELLED;
    }

    private String markFailed(EduAiTask task, String error, EduAiTaskProgressCallback callback)
    {
        if (taskMapper.markTerminal(task.getTaskId(), EduAiTaskStatus.FAILED, null, error) != 1)
        {
            throw new ServiceException("AI task failure transition was rejected");
        }
        callback.setTaskStatus(EduAiTaskStatus.FAILED);
        callback.onError(error);
        return EduAiTaskStatus.FAILED;
    }

    private EduAiTask requiredLockedTask(String taskId)
    {
        EduAiTask task = taskMapper.selectByTaskIdForUpdate(taskId);
        if (task == null)
        {
            throw new ServiceException("AI task not found during lifecycle update");
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolvedResources(EducationState finalState)
    {
        if (!finalState.activeResources().isEmpty())
        {
            return finalState.activeResources();
        }
        return (List<Map<String, Object>>) finalState
            .value(EducationState.GENERATED_RESOURCES).orElse(List.of());
    }

    private String sanitizeError(String message)
    {
        String value = message == null || message.isBlank() ? "AI task failed" : message;
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
