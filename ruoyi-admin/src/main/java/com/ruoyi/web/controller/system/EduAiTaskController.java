package com.ruoyi.web.controller.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduAiTask;
import com.ruoyi.system.domain.EduAiTaskEvent;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import com.ruoyi.system.service.IEduAiTaskService;
import com.ruoyi.web.controller.system.dto.ChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;

@RestController
@RequestMapping("/edu/ai/tasks")
public class EduAiTaskController extends BaseController
{
    @Autowired
    private IEduAiTaskService taskService;

    @PostMapping
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    public AjaxResult create(@Valid @RequestBody ChatRequest body)
    {
        EduAiTask task = taskService.createTask(SecurityUtils.getUserId(), body.getMessage(),
            body.getSessionId(), body.getAttachmentIds(), body.getClientRequestId());
        return success(taskView(task));
    }

    @GetMapping("/active")
    public AjaxResult active(@RequestParam(required = false) String sessionId)
    {
        List<Map<String, Object>> tasks = taskService
            .activeTasks(SecurityUtils.getUserId(), sessionId)
            .stream().map(this::taskView).toList();
        return success(tasks);
    }

    @GetMapping("/{taskId}")
    public AjaxResult get(@PathVariable String taskId)
    {
        return success(taskView(taskService.getTask(SecurityUtils.getUserId(), taskId)));
    }

    @GetMapping("/{taskId}/events")
    public AjaxResult events(@PathVariable String taskId,
                             @RequestParam(defaultValue = "0") Long afterSeq)
    {
        List<Map<String, Object>> events = taskService
            .getEvents(SecurityUtils.getUserId(), taskId, afterSeq)
            .stream().map(this::eventView).toList();
        return success(events);
    }

    @PostMapping("/{taskId}/cancel")
    public AjaxResult cancel(@PathVariable String taskId)
    {
        return success(taskView(taskService.cancel(SecurityUtils.getUserId(), taskId)));
    }

    private Map<String, Object> taskView(EduAiTask task)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("sessionId", task.getSessionId());
        result.put("clientRequestId", task.getClientRequestId());
        result.put("status", task.getStatus());
        result.put("cancelRequested", Boolean.TRUE.equals(task.getCancelRequested()));
        result.put("result", parseJson(task.getResultJson()));
        result.put("errorMessage", task.getErrorMessage());
        result.put("createTime", task.getCreateTime());
        result.put("startTime", task.getStartTime());
        result.put("finishTime", task.getFinishTime());
        result.put("updateTime", task.getUpdateTime());
        return result;
    }

    private Map<String, Object> eventView(EduAiTaskEvent event)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", event.getTaskId());
        result.put("sessionId", event.getSessionId());
        result.put("sequence", event.getSequence());
        result.put("eventType", event.getEventType());
        result.put("agentKey", event.getAgentKey());
        result.put("status", event.getStatus());
        result.put("payload", parseJson(event.getPayloadJson()));
        result.put("occurredTime", event.getOccurredTime());
        return result;
    }

    private Object parseJson(String value)
    {
        return value == null || value.isBlank() ? null : UserFacingTextSanitizer.sanitizeValue(JSON.parse(value));
    }
}
