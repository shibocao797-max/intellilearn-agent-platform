package com.ruoyi.system.ai.task;

/** Executable contract test for cancellation requests arriving before task registration. */
public final class EduAiTaskCancellationRegistryRaceTest
{
    public static void main(String[] args)
    {
        EduAiTaskCancellationRegistry registry = new EduAiTaskCancellationRegistry();

        registry.requestCancellation("before-register");
        EduAiTaskCancellationRegistry.CancellationToken token = registry.register("before-register");
        require(token.isCancellationRequested(), "pre-registration cancellation was lost");
        registry.unregister("before-register", token);

        registry.requestCancellation("never-started");
        registry.clear("never-started");
        EduAiTaskCancellationRegistry.CancellationToken cleared = registry.register("never-started");
        require(!cleared.isCancellationRequested(), "cleared pending cancellation was retained");
        registry.unregister("never-started", cleared);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
