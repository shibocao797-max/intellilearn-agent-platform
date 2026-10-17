package com.ruoyi.system.ai.task;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import com.ruoyi.system.domain.EduAiTaskEvent;
import com.ruoyi.system.mapper.EduAiTaskEventMapper;

/** Executable contract test for per-task event insert serialization and lock cleanup. */
public final class EduAiTaskEventStoreConcurrencyTest
{
    public static void main(String[] args) throws Exception
    {
        EduAiTaskEventStore store = new EduAiTaskEventStore();
        RecordingEventMapper mapper = new RecordingEventMapper();
        Field field = EduAiTaskEventStore.class.getDeclaredField("eventMapper");
        field.setAccessible(true);
        field.set(store, mapper);

        int workers = 8;
        int eventsPerWorker = 10;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++)
        {
            int workerId = worker;
            futures.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < eventsPerWorker; i++)
                {
                    store.append("task-serial", "session-1", "progress", "agent-" + workerId,
                        "RUNNING", java.util.Map.of("index", i));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures)
        {
            future.get();
        }
        executor.shutdownNow();

        require(mapper.inserts.get() == workers * eventsPerWorker, "event insert count mismatch");
        require(mapper.maxConcurrent.get() == 1, "same-task event inserts overlapped");
        require(store.activeTaskLockCount() == 0, "task lock was not cleaned up");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingEventMapper implements EduAiTaskEventMapper
    {
        private final AtomicInteger inserts = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();

        @Override
        public int insert(EduAiTaskEvent event)
        {
            int running = active.incrementAndGet();
            maxConcurrent.accumulateAndGet(running, Math::max);
            try
            {
                Thread.sleep(2L);
                event.setSequence((long) inserts.incrementAndGet());
                return 1;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            finally
            {
                active.decrementAndGet();
            }
        }

        @Override
        public List<EduAiTaskEvent> selectAfter(String taskId, Long afterSeq)
        {
            return List.of();
        }
    }
}
