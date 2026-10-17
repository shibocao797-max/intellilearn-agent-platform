package com.ruoyi.system.ai.graph;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.ruoyi.system.ai.agent.ResourceAgentRegistry;
import com.ruoyi.system.ai.agent.ResourceGenAgent;
import com.ruoyi.system.ai.agent.TextReplyAgent;
import com.ruoyi.system.ai.agent.QualityReviewAgent;
import org.springframework.core.task.TaskExecutor;

/** Executable contract test for failure and timeout isolation between peer agents. */
public final class EducationGraphBuilderIsolationTest
{
    public static void main(String[] args) throws Exception
    {
        CountDownLatch releaseBlockedAgent = new CountDownLatch(1);
        FakeResourceAgent fast = new FakeResourceAgent("fastGen", false, null);
        FakeResourceAgent failing = new FakeResourceAgent("failingGen", true, null);
        FakeResourceAgent blocked = new FakeResourceAgent("blockedGen", false, releaseBlockedAgent);
        ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "agent-isolation-test");
            thread.setDaemon(true);
            return thread;
        });

        try {
            EducationGraphBuilder builder = new EducationGraphBuilder();
            inject(builder, "agentRegistry", new ResourceAgentRegistry(List.of(fast, failing, blocked)));
            inject(builder, "textReplyAgent", new ImmediateTextReplyAgent());
            inject(builder, "qualityReviewAgent", new NoOpQualityReviewAgent());
            inject(builder, "asyncExecutor", (TaskExecutor) executor::execute);
            inject(builder, "agentTimeoutSeconds", 1L);

            EducationState input = new EducationState(Map.of(
                EducationState.USER_ID, 1L,
                EducationState.USER_MESSAGE, "test",
                EducationState.SESSION_ID, "session-1",
                EducationState.RUN_TRACE_ID, "trace-1",
                EducationState.SELECTED_AGENTS,
                    List.of("textReply", "fastGen", "failingGen", "blockedGen")));
            CapturingCallback callback = new CapturingCallback();

            long started = System.nanoTime();
            EducationState result = invokeDispatch(builder, input, callback);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            require(elapsedMs < 4000L, "blocked agent still held the whole dispatch");
            require("answer".equals(result.textReply()), "completed text reply was lost");
            require(fast.persistCalls.get() == 1, "completed resource was not persisted");
            require(failing.persistCalls.get() == 0, "failed resource reached persistence");
            require(blocked.persistCalls.get() == 0, "timed out resource reached persistence");
            require("completed".equals(status(result, "fastGen").get("status")),
                "completed peer status was lost");
            require("failed".equals(status(result, "failingGen").get("status")),
                "exception was not isolated as an agent failure");
            require("timeout".equals(status(result, "blockedGen").get("reason")),
                "timeout reason was not recorded");
            require(callback.resources.size() == 3,
                "partial success and both failed resource events were not emitted");

            Map<String, Object> reviewData = new HashMap<>(result.data());
            reviewData.put(EducationState.REVIEW_RESULTS, List.of(
                Map.of("agentName", "failingGen", "persistedId", "", "decision", "REVISE", "passed", false),
                Map.of("agentName", "blockedGen", "persistedId", "", "decision", "REVISE", "passed", false)));
            Map<String, Object> correction = invokeQualityCorrection(
                builder, new EducationState(reviewData));
            require(correction.get("active_resources") instanceof List<?> activeResources
                    && activeResources.size() == 1,
                "failed resources were retained for quality correction retry");

            EducationGraphBuilder emptyTextBuilder = new EducationGraphBuilder();
            inject(emptyTextBuilder, "agentRegistry", new ResourceAgentRegistry(List.of()));
            inject(emptyTextBuilder, "textReplyAgent", new EmptyTextReplyAgent());
            inject(emptyTextBuilder, "asyncExecutor", (TaskExecutor) executor::execute);
            inject(emptyTextBuilder, "agentTimeoutSeconds", 1L);
            EducationState emptyTextResult = invokeDispatch(emptyTextBuilder, new EducationState(Map.of(
                EducationState.USER_ID, 1L,
                EducationState.USER_MESSAGE, "empty",
                EducationState.SESSION_ID, "session-1",
                EducationState.SELECTED_AGENTS, List.of("textReply"))), new CapturingCallback());
            require(emptyTextResult.textReply().isEmpty(),
                "empty streaming response triggered an unbounded synchronous fallback");

            CountDownLatch releasePersist = new CountDownLatch(1);
            CountDownLatch persistStarted = new CountDownLatch(1);
            PersistBlockingAgent persistBlocking = new PersistBlockingAgent(releasePersist, persistStarted);
            EducationGraphBuilder persistBuilder = new EducationGraphBuilder();
            inject(persistBuilder, "agentRegistry", new ResourceAgentRegistry(List.of(persistBlocking)));
            inject(persistBuilder, "textReplyAgent", new ImmediateTextReplyAgent());
            inject(persistBuilder, "asyncExecutor", (TaskExecutor) executor::execute);
            inject(persistBuilder, "agentTimeoutSeconds", 1L);
            long persistStartedAt = System.nanoTime();
            invokeDispatch(persistBuilder, new EducationState(Map.of(
                EducationState.USER_ID, 1L,
                EducationState.USER_MESSAGE, "persist timeout",
                EducationState.SESSION_ID, "session-1",
                EducationState.SELECTED_AGENTS, List.of("textReply", "persistGen"))), new CapturingCallback());
            long persistElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - persistStartedAt);
            require(persistStarted.await(2L, TimeUnit.SECONDS), "blocking persistence did not start");
            require(persistElapsedMs < 4000L, "blocking persistence held the whole dispatch");
            releasePersist.countDown();

            releaseBlockedAgent.countDown();
            TimeUnit.MILLISECONDS.sleep(200L);
            require(blocked.persistCalls.get() == 0,
                "timed out agent persisted a late result after dispatch completed");
        }
        finally {
            releaseBlockedAgent.countDown();
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeQualityCorrection(EducationGraphBuilder builder,
                                                               EducationState state) throws Exception
    {
        Method method = EducationGraphBuilder.class.getDeclaredMethod(
            "qualityCorrection", EducationState.class, AgentProgressCallback.class);
        method.setAccessible(true);
        try {
            return (Map<String, Object>) method.invoke(builder, state, null);
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static EducationState invokeDispatch(EducationGraphBuilder builder, EducationState state,
                                                 AgentProgressCallback callback) throws Exception
    {
        Method method = EducationGraphBuilder.class.getDeclaredMethod(
            "dispatchAgentsWithProgress", EducationState.class, AgentProgressCallback.class);
        method.setAccessible(true);
        try {
            return (EducationState) method.invoke(builder, state, callback);
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> status(EducationState state, String agentName)
    {
        return (Map<String, Object>) state.agentStatuses().get(agentName);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ImmediateTextReplyAgent extends TextReplyAgent
    {
        @Override
        public void generateStreaming(EducationState state, Consumer<String> onToken,
                                      Consumer<String> onComplete, Consumer<Throwable> onError)
        {
            onToken.accept("answer");
            onComplete.accept("answer");
        }

        @Override
        public String generate(EducationState state)
        {
            return "answer";
        }
    }

    private static final class EmptyTextReplyAgent extends TextReplyAgent
    {
        @Override
        public void generateStreaming(EducationState state, Consumer<String> onToken,
                                      Consumer<String> onComplete, Consumer<Throwable> onError)
        {
            onComplete.accept("");
        }

        @Override
        public String generate(EducationState state)
        {
            throw new AssertionError("synchronous fallback must not be called");
        }
    }

    private static final class FakeResourceAgent implements ResourceGenAgent
    {
        private final String name;
        private final boolean fail;
        private final CountDownLatch release;
        private final AtomicInteger persistCalls = new AtomicInteger();

        private FakeResourceAgent(String name, boolean fail, CountDownLatch release)
        {
            this.name = name;
            this.fail = fail;
            this.release = release;
        }

        @Override public String agentName() { return name; }
        @Override public String resourceType() { return "test"; }
        @Override public String displayName() { return name; }
        @Override public String agentRole() { return "test"; }
        @Override public String[] capabilities() { return new String[]{"test"}; }
        @Override public String[] tools() { return new String[0]; }
        @Override public boolean shouldGenerate(EducationState state) { return true; }
        @Override public String buildSystemPrompt(EducationState state) { return ""; }
        @Override public String buildUserPrompt(EducationState state) { return ""; }

        @Override
        public String generate(EducationState state)
        {
            if (fail) {
                throw new IllegalStateException("expected failure");
            }
            if (release != null) {
                boolean interrupted = false;
                while (release.getCount() > 0L) {
                    try {
                        release.await();
                    }
                    catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return name + " result";
        }

        @Override
        public String persist(EducationState state, String result)
        {
            persistCalls.incrementAndGet();
            return name + "-id";
        }
    }

    private static final class PersistBlockingAgent implements ResourceGenAgent
    {
        private final CountDownLatch release;
        private final CountDownLatch started;

        private PersistBlockingAgent(CountDownLatch release, CountDownLatch started)
        {
            this.release = release;
            this.started = started;
        }

        @Override public String agentName() { return "persistGen"; }
        @Override public String resourceType() { return "test"; }
        @Override public String displayName() { return "persistGen"; }
        @Override public String agentRole() { return "test"; }
        @Override public String[] capabilities() { return new String[]{"test"}; }
        @Override public String[] tools() { return new String[0]; }
        @Override public boolean shouldGenerate(EducationState state) { return true; }
        @Override public String buildSystemPrompt(EducationState state) { return ""; }
        @Override public String buildUserPrompt(EducationState state) { return ""; }
        @Override public String generate(EducationState state) { return "persist result"; }

        @Override
        public String persist(EducationState state, String result)
        {
            started.countDown();
            boolean interrupted = false;
            while (release.getCount() > 0L) {
                try {
                    release.await();
                }
                catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return "persist-id";
        }
    }

    private static final class NoOpQualityReviewAgent extends QualityReviewAgent
    {
        @Override
        public List<Map<String, Object>> review(EducationState state, List<Map<String, Object>> resources)
        {
            return List.of();
        }

        @Override
        public Map<String, Object> summary(List<Map<String, Object>> reviews)
        {
            return new LinkedHashMap<>();
        }
    }

    private static final class CapturingCallback implements AgentProgressCallback
    {
        private final Map<String, String> progress = new ConcurrentHashMap<>();
        private final List<Map<String, Object>> resources = new CopyOnWriteArrayList<>();

        @Override public void onPhase(String phase) {}
        @Override public void onIntent(String intent, String reason) {}
        @Override public void onSelectedAgents(List<String> agentNames) {}
        @Override public void onAgentProgress(String agentName, String displayName,
                                              String status, Long durationMs) {
            progress.put(agentName, status);
        }
        @Override public void onResource(Map<String, Object> resourceData) { resources.add(resourceData); }
        @Override public void onToken(String token) {}
        @Override public void onTokenDone() {}
        @Override public void onDone() {}
        @Override public void onError(String msg) {}
        @Override public boolean isDisconnected() { return false; }
    }
}
