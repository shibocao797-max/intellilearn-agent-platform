package com.ruoyi.system.ai.task;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.ai.graph.EducationGraphBuilder;
import com.ruoyi.system.domain.EduAiTask;
import com.ruoyi.system.domain.EduAiTaskStatus;
import com.ruoyi.system.mapper.EduAiTaskMapper;
import org.springframework.core.task.TaskExecutor;

/** Executable contract test for single-instance restart recovery classification. */
public final class EduAiTaskRunnerRecoveryTest
{
    public static void main(String[] args) throws Exception
    {
        EduAiTask queued = task("queued-task", EduAiTaskStatus.QUEUED);
        EduAiTask running = task("running-task", EduAiTaskStatus.RUNNING);
        FakeTaskMapper mapper = new FakeTaskMapper(List.of(queued, running));
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        List<Runnable> scheduled = new ArrayList<>();

        EduAiTaskRunner runner = new EduAiTaskRunner();
        inject(runner, "taskMapper", mapper);
        inject(runner, "lifecycleService", lifecycle);
        inject(runner, "taskExecutor", (TaskExecutor) scheduled::add);
        inject(runner, "graphBuilder", new EducationGraphBuilder());
        runner.recoverTasksAfterRestart();

        require(scheduled.size() == 1, "QUEUED task was not resubmitted");
        require(lifecycle.failedInterrupted.equals(List.of("running-task")),
            "RUNNING task was not classified as interrupted");
    }

    private static EduAiTask task(String id, String status)
    {
        EduAiTask task = new EduAiTask();
        task.setTaskId(id);
        task.setSessionId("session-1");
        task.setStatus(status);
        task.setCancelRequested(false);
        return task;
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

    private static final class RecordingLifecycle extends EduAiTaskLifecycleService
    {
        private final List<String> failedInterrupted = new ArrayList<>();

        @Override
        public void failInterrupted(String taskId)
        {
            failedInterrupted.add(taskId);
        }
    }

    private static final class FakeTaskMapper implements EduAiTaskMapper
    {
        private final List<EduAiTask> tasks;

        private FakeTaskMapper(List<EduAiTask> tasks)
        {
            this.tasks = tasks;
        }

        @Override public List<EduAiTask> selectInterrupted() { return tasks; }
        @Override public EduAiTask selectByTaskId(String taskId) { return find(taskId); }
        @Override public int markRunning(String taskId) { return 0; }
        @Override public EduAiTask selectByTaskIdForUpdate(String taskId) { return find(taskId); }
        @Override public EduAiTask selectOwned(String taskId, Long userId) { return null; }
        @Override public EduAiTask selectByUserAndClientRequestId(Long userId, String id) { return null; }
        @Override public EduAiTask selectByUserAndClientRequestIdForUpdate(Long userId, String id) { return null; }
        @Override public List<EduAiTask> selectActive(Long userId, String sessionId) { return List.of(); }
        @Override public int insertOrIgnore(EduAiTask task) { return 0; }
        @Override public int requestCancel(String taskId, Long userId) { return 0; }
        @Override public Boolean selectCancelRequested(String taskId) { return false; }
        @Override public int markQueuedCancelled(String taskId, Long userId) { return 0; }
        @Override public int markTerminal(String taskId, String status, String resultJson, String errorMessage) { return 0; }

        private EduAiTask find(String taskId)
        {
            return tasks.stream().filter(task -> task.getTaskId().equals(taskId)).findFirst().orElse(null);
        }
    }
}
