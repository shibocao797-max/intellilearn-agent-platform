package com.ruoyi.system.ai.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.ai.graph.EducationGraphBuilder;
import com.ruoyi.system.ai.graph.EducationState;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import com.ruoyi.system.ai.task.EduAiTaskCancellationRegistry.CancellationToken;
import com.ruoyi.system.domain.EduAiTask;
import com.ruoyi.system.domain.EduAiTaskStatus;
import com.ruoyi.system.mapper.EduAiTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Schedules committed tasks and runs the graph independently of HTTP connections. */
@Component
public class EduAiTaskRunner
{
    private static final Logger log = LoggerFactory.getLogger(EduAiTaskRunner.class);

    @Autowired private EduAiTaskMapper taskMapper;
    @Autowired private EducationGraphBuilder graphBuilder;
    @Autowired private EduAiTaskEventStore eventStore;
    @Autowired private EduAiTaskLifecycleService lifecycleService;
    @Autowired private EduAiTaskCancellationRegistry cancellationRegistry;
    @Autowired @Qualifier("eduAiTaskExecutor") private TaskExecutor taskExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskSubmitted(EduAiTaskSubmittedEvent event)
    {
        submit(event.taskId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancellationRequested(EduAiTaskCancellationEvent event)
    {
        cancellationRegistry.requestCancellation(event.taskId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverTasksAfterRestart()
    {
        List<EduAiTask> interrupted = taskMapper.selectInterrupted();
        int resubmitted = 0;
        int failed = 0;
        for (EduAiTask task : interrupted)
        {
            try
            {
                if (EduAiTaskStatus.QUEUED.equals(task.getStatus()))
                {
                    if (Boolean.TRUE.equals(task.getCancelRequested()))
                    {
                        failBeforeStart(task.getTaskId(), "Task cancelled before restart recovery");
                    }
                    else
                    {
                        // Single-instance recovery: no cross-instance lease is claimed here.
                        submit(task.getTaskId());
                        resubmitted++;
                    }
                }
                else if (EduAiTaskStatus.RUNNING.equals(task.getStatus()))
                {
                    lifecycleService.failInterrupted(task.getTaskId());
                    failed++;
                }
            }
            catch (Exception e)
            {
                log.error("Failed to recover interrupted AI task {}: {}",
                    task.getTaskId(), e.getMessage(), e);
            }
        }
        if (resubmitted > 0 || failed > 0)
        {
            log.warn("Restart recovery resubmitted {} QUEUED task(s) and marked {} RUNNING task(s) as FAILED",
                resubmitted, failed);
        }
    }

    public void submit(String taskId)
    {
        try
        {
            taskExecutor.execute(() -> execute(taskId));
        }
        catch (RuntimeException e)
        {
            log.error("AI task executor rejected task {}: {}", taskId, e.getMessage(), e);
            failBeforeStart(taskId, "AI task executor is at capacity");
        }
    }

    private void execute(String taskId)
    {
        if (taskMapper.markRunning(taskId) != 1)
        {
            cancellationRegistry.clear(taskId);
            return;
        }
        EduAiTask task = taskMapper.selectByTaskId(taskId);
        if (task == null)
        {
            return;
        }

        CancellationToken token = cancellationRegistry.register(taskId);
        EduAiTaskProgressCallback callback = new EduAiTaskProgressCallback(
            taskId, task.getSessionId(), EduAiTaskStatus.RUNNING, eventStore, token);
        try
        {
            if (Boolean.TRUE.equals(taskMapper.selectCancelRequested(taskId)))
            {
                token.requestCancellation();
                lifecycleService.fail(taskId, "Task cancelled before execution", callback);
                return;
            }
            callback.onPhase("START");
            String graphMessage = task.getMessage() == null || task.getMessage().isBlank()
                ? "Please answer based on my uploaded attachment."
                : task.getMessage();
            EducationState finalState = graphBuilder.executeWithProgress(
                task.getUserId(), graphMessage, task.getSessionId(),
                task.getAttachmentContext(), callback);

            if (Boolean.TRUE.equals(taskMapper.selectCancelRequested(taskId)))
            {
                token.requestCancellation();
            }
            lifecycleService.complete(taskId, finalState, result(finalState), callback);
        }
        catch (Exception e)
        {
            log.error("AI background task {} failed: {}", taskId, e.getMessage(), e);
            try
            {
                lifecycleService.fail(taskId, rootMessage(e), callback);
            }
            catch (Exception terminalError)
            {
                log.error("AI task {} terminal update failed: {}",
                    taskId, terminalError.getMessage(), terminalError);
            }
        }
        finally
        {
            cancellationRegistry.unregister(taskId, token);
        }
    }

    private void failBeforeStart(String taskId, String message)
    {
        EduAiTask task = taskMapper.selectByTaskId(taskId);
        if (task == null)
        {
            return;
        }
        CancellationToken token = new CancellationToken();
        EduAiTaskProgressCallback callback = new EduAiTaskProgressCallback(
            taskId, task.getSessionId(), task.getStatus(), eventStore, token);
        try
        {
            lifecycleService.fail(taskId, message, callback);
        }
        catch (Exception e)
        {
            log.error("Could not mark rejected AI task {} as failed: {}", taskId, e.getMessage(), e);
        }
    }

    private Map<String, Object> result(EducationState state)
    {
        if (state == null)
        {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runTraceId", state.runTraceId());
        result.put("intent", state.intent());
        result.put("selectedAgents", state.selectedAgents());
        result.put("recommendReason", state.recommendReason());
        List<Map<String, Object>> resources = resolvedResources(state).stream()
            .filter(resource -> !isGenerationFailure(resource))
            .map(resource -> (Map<String, Object>) UserFacingTextSanitizer.sanitizeValue(resource))
            .toList();
        result.put("generatedResources", resources);
        result.put("resourceCount", resources.size());
        result.put("activeResources", resources);
        result.put("reviewResults", state.reviewResults());
        result.put("qualityCorrections", state.qualityCorrections());
        result.put("learningPath", state.learningPath());
        result.put("resourcePlan", state.resourcePlan());
        result.put("finalRecommendations", state.finalRecommendations());
        result.put("agentMessages", state.agentMessages());
        result.put("coordinationTrace", state.coordinationTrace());
        result.put("agentStatuses", state.agentStatuses());
        result.put("textReply", UserFacingTextSanitizer.sanitize(state.textReply()));
        result.put("profileJson", state.profileJson());
        result.put("currentPhase", state.currentPhase());
        return (Map<String, Object>) UserFacingTextSanitizer.sanitizeValue(result);
    }

    /**
     * 生成成功与质量审核是两个独立结果。
     * 审核未通过的资源仍然是已生成内容，不能从任务结果中移除；这里只隐藏
     * 明确的生成/媒体失败结果。
     */
    private boolean isGenerationFailure(Map<String, Object> resource)
    {
        if (resource == null)
        {
            return true;
        }
        if (Boolean.FALSE.equals(resource.get("success"))
            || "false".equalsIgnoreCase(String.valueOf(resource.get("success"))))
        {
            return true;
        }
        return isFailureMarker(resource.get("generationStatus"))
            || isFailureMarker(resource.get("mediaStatus"))
            || isFailureMarker(resource.get("status"));
    }

    private boolean isFailureMarker(Object value)
    {
        if (value == null)
        {
            return false;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        return "failed".equals(normalized)
            || "error".equals(normalized)
            || "cancelled".equals(normalized)
            || "config_missing".equals(normalized);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolvedResources(EducationState state)
    {
        if (!state.activeResources().isEmpty())
        {
            return state.activeResources();
        }
        return (List<Map<String, Object>>) state
            .value(EducationState.GENERATED_RESOURCES).orElse(List.of());
    }

    private String rootMessage(Throwable error)
    {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
            ? current.getClass().getSimpleName()
            : current.getMessage();
    }
}
