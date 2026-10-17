package com.ruoyi.system.ai.task;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** In-process cancellation signal backed by the durable cancel_requested flag. */
@Component
public class EduAiTaskCancellationRegistry
{
    private final ConcurrentMap<String, CancellationToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> pendingCancellations = new ConcurrentHashMap<>();

    public synchronized CancellationToken register(String taskId)
    {
        CancellationToken token = new CancellationToken();
        CancellationToken previous = tokens.putIfAbsent(taskId, token);
        CancellationToken selected = previous == null ? token : previous;
        if (pendingCancellations.remove(taskId) != null)
        {
            selected.requestCancellation();
        }
        return selected;
    }

    public synchronized void requestCancellation(String taskId)
    {
        CancellationToken token = tokens.get(taskId);
        if (token != null)
        {
            token.requestCancellation();
            return;
        }
        pendingCancellations.put(taskId, Boolean.TRUE);
    }

    public synchronized void unregister(String taskId, CancellationToken token)
    {
        tokens.remove(taskId, token);
        pendingCancellations.remove(taskId);
    }

    public synchronized void clear(String taskId)
    {
        pendingCancellations.remove(taskId);
    }

    public static final class CancellationToken
    {
        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

        public void requestCancellation()
        {
            cancellationRequested.set(true);
        }

        public boolean isCancellationRequested()
        {
            return cancellationRequested.get();
        }
    }
}
