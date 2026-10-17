package com.ruoyi.system.ai.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.ai.task.EduAiTaskCancellationRegistry.CancellationToken;
import com.ruoyi.system.domain.EduAiTaskEvent;
import com.ruoyi.system.domain.EduAiTaskStatus;

/** Executable callback and cancellation contract test. */
public final class EduAiTaskProgressCallbackTest
{
    public static void main(String[] args)
    {
        CapturingEventStore eventStore = new CapturingEventStore();
        CancellationToken token = new CancellationToken();
        EduAiTaskProgressCallback callback = new EduAiTaskProgressCallback(
            "task-1", "session-1", EduAiTaskStatus.RUNNING, eventStore, token);

        require(!callback.isDisconnected(), "callback started cancelled");
        callback.onAgentProgress("quizGen", "Quiz generator", "running", null);
        callback.onSelectedAgents(List.of("quizGen"));
        callback.onToken("a".repeat(31));
        require(eventStore.events.size() == 2, "token batch flushed before reaching its boundary");
        callback.onToken("b");
        require(eventStore.events.size() == 3, "token batch did not flush at the boundary");
        require(eventStore.payloads.get(2) instanceof Map<?, ?> tokenPayload
            && "a".repeat(31).concat("b").equals(tokenPayload.get("token")),
            "token batch payload was not preserved");
        callback.onToken("discarded-after-cancel");
        token.requestCancellation();
        require(callback.isDisconnected(), "cancel request did not stop the callback");
        callback.setTaskStatus(EduAiTaskStatus.CANCELLED);
        callback.onToken("must-not-be-persisted-after-cancel".repeat(2));
        callback.onAgentProgress("quizGen", "Quiz generator", "completed", 1L);
        callback.onDone();

        require(eventStore.events.size() == 4, "unexpected event count");
        EduAiTaskEvent progress = eventStore.events.get(0);
        require("task-1".equals(progress.getTaskId()), "taskId missing from event");
        require("session-1".equals(progress.getSessionId()), "sessionId missing from event");
        require("agentProgress".equals(progress.getEventType()), "wrong progress event type");
        require("quizGen".equals(progress.getAgentKey()), "agent key was not preserved");
        require(EduAiTaskStatus.RUNNING.equals(progress.getStatus()), "running status missing");
        require(eventStore.payloads.get(1) instanceof Map<?, ?> selectedPayload
            && selectedPayload.containsKey("selectedAgents"),
            "selectedAgents payload field does not match the client contract");
        require(EduAiTaskStatus.CANCELLED.equals(eventStore.events.get(3).getStatus()),
            "terminal event did not use terminal task status");
        require(eventStore.payloads.get(3) instanceof Map<?, ?> donePayload
            && EduAiTaskStatus.CANCELLED.equals(donePayload.get("status")),
            "done payload did not include CANCELLED status");

        CapturingEventStore successStore = new CapturingEventStore();
        EduAiTaskProgressCallback successCallback = new EduAiTaskProgressCallback(
            "task-2", "session-1", EduAiTaskStatus.RUNNING, successStore, new CancellationToken());
        successCallback.setTaskStatus(EduAiTaskStatus.SUCCEEDED);
        successCallback.onDone();
        require(successStore.payloads.get(0) instanceof Map<?, ?> successPayload
            && EduAiTaskStatus.SUCCEEDED.equals(successPayload.get("status")),
            "done payload did not include SUCCEEDED status");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static final class CapturingEventStore extends EduAiTaskEventStore
    {
        private final List<EduAiTaskEvent> events = new ArrayList<>();
        private final List<Object> payloads = new ArrayList<>();

        @Override
        public EduAiTaskEvent append(String taskId, String sessionId, String eventType,
                                     String agentKey, String status, Object payload)
        {
            EduAiTaskEvent event = new EduAiTaskEvent();
            event.setTaskId(taskId);
            event.setSessionId(sessionId);
            event.setEventType(eventType);
            event.setAgentKey(agentKey);
            event.setStatus(status);
            event.setPayloadJson(String.valueOf(payload == null ? Map.of() : payload));
            events.add(event);
            payloads.add(payload);
            return event;
        }
    }
}
