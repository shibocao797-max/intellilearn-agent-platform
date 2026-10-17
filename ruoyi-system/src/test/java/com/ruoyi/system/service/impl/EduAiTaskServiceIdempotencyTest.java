package com.ruoyi.system.service.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.ai.service.ConversationService;
import com.ruoyi.system.ai.task.EduAiTaskEventStore;
import com.ruoyi.system.domain.EduAiTask;
import com.ruoyi.system.domain.EduAiTaskEvent;
import com.ruoyi.system.domain.EduAiTaskStatus;
import com.ruoyi.system.domain.EduChatAttachment;
import com.ruoyi.system.mapper.EduAiTaskMapper;
import com.ruoyi.system.service.IEduChatAttachmentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

/** Executable contract test that needs no additional test dependency. */
public final class EduAiTaskServiceIdempotencyTest
{
    public static void main(String[] args) throws Exception
    {
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeConversationService conversation = new FakeConversationService();
        FakeEventStore eventStore = new FakeEventStore();
        List<Object> published = new ArrayList<>();

        EduAiTaskServiceImpl service = new EduAiTaskServiceImpl();
        inject(service, "taskMapper", taskMapper);
        inject(service, "eventStore", eventStore);
        inject(service, "conversationService", conversation);
        inject(service, "attachmentService", new EmptyAttachmentService());
        inject(service, "eventPublisher", (ApplicationEventPublisher) published::add);

        EduAiTask first = service.createTask(7L, "first message", "session-a", List.of(), "request-1");
        EduAiTask replay = service.createTask(7L, "different message", "session-b", List.of(), "request-1");

        require(first.getTaskId().equals(replay.getTaskId()), "idempotent replay returned another task");
        require(EduAiTaskStatus.QUEUED.equals(first.getStatus()), "new task was not queued");
        require(taskMapper.tasksById.size() == 1, "duplicate task row was created");
        require(conversation.savedUserMessages == 1, "duplicate user message was persisted");
        require(eventStore.events.size() == 1, "duplicate queued event was persisted");
        require(published.size() == 1, "duplicate background execution was published");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static final class FakeConversationService extends ConversationService
    {
        private int savedUserMessages;

        @Override
        public void saveUserMessageWithAttachments(Long userId, String sessionId, String content,
                                                   List<Map<String, Object>> attachments)
        {
            savedUserMessages++;
        }

        @Override
        public String newSessionId()
        {
            return "generated-session";
        }
    }

    private static final class FakeEventStore extends EduAiTaskEventStore
    {
        private final List<EduAiTaskEvent> events = new ArrayList<>();

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
            events.add(event);
            return event;
        }
    }

    private static final class EmptyAttachmentService implements IEduChatAttachmentService
    {
        @Override public EduChatAttachment upload(Long userId, String sessionId, MultipartFile file) { throw new UnsupportedOperationException(); }
        @Override public EduChatAttachment getOwned(Long userId, Long attachmentId) { throw new UnsupportedOperationException(); }
        @Override public List<EduChatAttachment> validateForChat(Long userId, String sessionId, List<Long> attachmentIds) { return List.of(); }
        @Override public void delete(Long userId, Long attachmentId) { throw new UnsupportedOperationException(); }
        @Override public void deleteSessionAttachments(Long userId, String sessionId) { throw new UnsupportedOperationException(); }
        @Override public Map<String, Object> publicMetadata(EduChatAttachment attachment) { return Map.of(); }
    }

    private static final class FakeTaskMapper implements EduAiTaskMapper
    {
        private final Map<String, EduAiTask> tasksById = new LinkedHashMap<>();
        private final Map<String, String> taskIdByRequest = new LinkedHashMap<>();

        @Override
        public int insertOrIgnore(EduAiTask task)
        {
            String key = task.getUserId() + ":" + task.getClientRequestId();
            if (taskIdByRequest.containsKey(key)) return 0;
            tasksById.put(task.getTaskId(), task);
            taskIdByRequest.put(key, task.getTaskId());
            return 1;
        }

        @Override public EduAiTask selectByTaskId(String taskId) { return tasksById.get(taskId); }
        @Override public EduAiTask selectByTaskIdForUpdate(String taskId) { return tasksById.get(taskId); }
        @Override public EduAiTask selectOwned(String taskId, Long userId) {
            EduAiTask task = tasksById.get(taskId);
            return task != null && userId.equals(task.getUserId()) ? task : null;
        }
        @Override public EduAiTask selectByUserAndClientRequestId(Long userId, String clientRequestId) {
            return tasksById.get(taskIdByRequest.get(userId + ":" + clientRequestId));
        }
        @Override public EduAiTask selectByUserAndClientRequestIdForUpdate(Long userId, String clientRequestId) {
            return selectByUserAndClientRequestId(userId, clientRequestId);
        }
        @Override public List<EduAiTask> selectActive(Long userId, String sessionId) { return List.of(); }
        @Override public List<EduAiTask> selectInterrupted() { return List.of(); }
        @Override public int markRunning(String taskId) { return 0; }
        @Override public int requestCancel(String taskId, Long userId) { return 0; }
        @Override public Boolean selectCancelRequested(String taskId) { return Boolean.FALSE; }
        @Override public int markQueuedCancelled(String taskId, Long userId) { return 0; }
        @Override public int markTerminal(String taskId, String status, String resultJson, String errorMessage) { return 0; }
    }
}
