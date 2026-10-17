package com.ruoyi.system.ai.task;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.system.domain.EduAiTaskEvent;
import com.ruoyi.system.mapper.EduAiTaskEventMapper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Single event persistence entry point used by callbacks and lifecycle code. */
@Component
public class EduAiTaskEventStore
{
    /**
     * Serializes inserts for one task inside this application instance. This project currently
     * runs task execution on a single backend instance; multi-instance execution would require a
     * database-backed task sequence or distributed lock instead.
     */
    private final ConcurrentMap<String, TaskInsertLock> taskLocks = new ConcurrentHashMap<>();

    @Autowired
    private EduAiTaskEventMapper eventMapper;

    public EduAiTaskEvent append(String taskId, String sessionId, String eventType,
                                 String agentKey, String status, Object payload)
    {
        TaskInsertLock taskLock = taskLocks.compute(taskId, (key, current) ->
        {
            TaskInsertLock retained = current == null ? new TaskInsertLock() : current;
            retained.retain();
            return retained;
        });
        taskLock.lock.lock();
        try
        {
            EduAiTaskEvent event = new EduAiTaskEvent();
            event.setTaskId(taskId);
            event.setSessionId(sessionId);
            event.setEventType(eventType);
            event.setAgentKey(agentKey);
            event.setStatus(status);
            event.setPayloadJson(JSON.toJSONString(payload == null ? java.util.Map.of() : payload));
            eventMapper.insert(event);
            return event;
        }
        finally
        {
            taskLock.lock.unlock();
            taskLocks.computeIfPresent(taskId, (key, current) ->
            {
                if (current != taskLock)
                {
                    return current;
                }
                return current.release() == 0 ? null : current;
            });
        }
    }

    int activeTaskLockCount()
    {
        return taskLocks.size();
    }

    private static final class TaskInsertLock
    {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;

        private void retain()
        {
            references++;
        }

        private int release()
        {
            return --references;
        }
    }
}
