package com.ruoyi.system.ai.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.ruoyi.system.ai.graph.AgentProgressCallback;
import com.ruoyi.system.ai.support.UserFacingTextSanitizer;
import com.ruoyi.system.ai.task.EduAiTaskCancellationRegistry.CancellationToken;

/** Persists every graph callback while cancellation remains independent of clients. */
public class EduAiTaskProgressCallback implements AgentProgressCallback
{
    private static final int TOKEN_BATCH_SIZE = 32;

    private final String taskId;
    private final String sessionId;
    private final EduAiTaskEventStore eventStore;
    private final CancellationToken cancellationToken;
    private final AtomicReference<String> taskStatus;
    private final Object tokenLock = new Object();
    private final StringBuilder tokenBuffer = new StringBuilder();

    public EduAiTaskProgressCallback(String taskId, String sessionId, String initialStatus,
                                     EduAiTaskEventStore eventStore, CancellationToken cancellationToken)
    {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.eventStore = eventStore;
        this.cancellationToken = cancellationToken;
        this.taskStatus = new AtomicReference<>(initialStatus);
    }

    public void setTaskStatus(String status)
    {
        taskStatus.set(status);
    }

    @Override
    public void onPhase(String phase)
    {
        append("phase", null, Map.of("phase", phase));
    }

    @Override
    public void onIntent(String intent, String reason)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", intent);
        payload.put("recommendReason", reason);
        append("intent", null, payload);
    }

    @Override
    public void onSelectedAgents(List<String> agentNames)
    {
        append("selectedAgents", null,
            Map.of("selectedAgents", agentNames == null ? List.of() : agentNames));
    }

    @Override
    public void onAgentProgress(String agentName, String displayName, String status, Long durationMs)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentName", agentName);
        payload.put("displayName", displayName);
        payload.put("status", status);
        if (durationMs != null)
        {
            payload.put("durationMs", durationMs);
        }
        append("agentProgress", agentName, payload);
    }

    @Override
    public void onResource(Map<String, Object> resourceData)
    {
        append("resource", agentKey(resourceData), resourceData);
    }

    @Override
    public void onAgentMessage(Map<String, Object> messageData)
    {
        append("agentMessage", firstString(messageData, "fromAgent", "agentName", "agentKey"), messageData);
    }

    @Override
    public void onReview(Map<String, Object> reviewData)
    {
        append("review", agentKey(reviewData), reviewData);
    }

    @Override
    public void onRevisionStart(Map<String, Object> revisionData)
    {
        append("revisionStart", agentKey(revisionData), revisionData);
    }

    @Override
    public void onRevisionResource(Map<String, Object> resourceData)
    {
        append("revisionResource", agentKey(resourceData), resourceData);
    }

    @Override
    public void onRevisionDone(Map<String, Object> revisionSummary)
    {
        append("revisionDone", null, revisionSummary);
    }

    @Override
    public void onLearningPath(Map<String, Object> pathData)
    {
        append("learningPath", "learningPath", pathData);
    }

    @Override
    public void onResourcePlan(Map<String, Object> planData)
    {
        append("resourcePlan", "resourcePlanner", planData);
    }

    @Override
    public void onFinalRecommendations(Map<String, Object> recommendationData)
    {
        append("finalRecommendations", "finalRecommendation", recommendationData);
    }

    @Override
    public void onToken(String token)
    {
        if (token == null || token.isEmpty() || isDisconnected())
        {
            return;
        }
        synchronized (tokenLock)
        {
            if (isDisconnected())
            {
                tokenBuffer.setLength(0);
                return;
            }
            tokenBuffer.append(token);
            if (tokenBuffer.length() >= TOKEN_BATCH_SIZE)
            {
                flushTokensLocked();
            }
        }
    }

    @Override
    public void onTokenDone()
    {
        append("tokenDone", "textReply", Map.of());
    }

    @Override
    public void onDone()
    {
        append("done", null, Map.of("completed", true, "status", taskStatus.get()));
    }

    @Override
    public void onError(String msg)
    {
        append("error", null, Map.of("message", msg == null ? "Unknown error" : msg));
    }

    @Override
    public boolean isDisconnected()
    {
        return cancellationToken.isCancellationRequested();
    }

    private void append(String eventType, String agentKey, Object payload)
    {
        boolean terminalEvent = "done".equals(eventType) || "error".equals(eventType);
        if (isDisconnected())
        {
            discardTokens();
            if (!terminalEvent)
            {
                return;
            }
        }
        else if (!"token".equals(eventType))
        {
            flushTokens();
        }
        eventStore.append(taskId, sessionId, eventType, agentKey, taskStatus.get(),
            UserFacingTextSanitizer.sanitizeValue(payload));
    }

    private void discardTokens()
    {
        synchronized (tokenLock)
        {
            tokenBuffer.setLength(0);
        }
    }

    private void flushTokens()
    {
        synchronized (tokenLock)
        {
            flushTokensLocked();
        }
    }

    private void flushTokensLocked()
    {
        if (isDisconnected())
        {
            tokenBuffer.setLength(0);
            return;
        }
        if (tokenBuffer.length() == 0)
        {
            return;
        }
        String chunk = tokenBuffer.toString();
        tokenBuffer.setLength(0);
        eventStore.append(taskId, sessionId, "token", "textReply",
            taskStatus.get(), Map.of("token", UserFacingTextSanitizer.sanitize(chunk)));
    }

    private String agentKey(Map<String, Object> payload)
    {
        return firstString(payload, "agentName", "agentKey", "sourceAgent");
    }

    private String firstString(Map<String, Object> payload, String... keys)
    {
        if (payload == null)
        {
            return null;
        }
        for (String key : keys)
        {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank())
            {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
