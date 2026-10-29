package com.ruoyi.system;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Bridges the repository's lightweight executable contract tests into Maven.
 *
 * <p>The individual tests intentionally remain dependency-light and expose a
 * {@code main} method. This adapter makes them visible to Surefire without
 * rewriting their hand-built fakes and reflection fixtures in one risky sweep.</p>
 */
class ExecutableContractTests
{
    @Test
    void educationGraphBuilderIsolation() throws Exception
    {
        run("com.ruoyi.system.ai.graph.EducationGraphBuilderIsolationTest");
    }

    @Test
    void conversationServiceTransaction() throws Exception
    {
        run("com.ruoyi.system.ai.service.ConversationServiceTransactionTest");
    }

    @Test
    void userAccountProfileServiceContract() throws Exception
    {
        run("com.ruoyi.system.ai.service.UserAccountProfileServiceContractTest");
    }

    @Test
    void userWorkspaceServiceContract() throws Exception
    {
        run("com.ruoyi.system.ai.service.UserWorkspaceServiceContractTest");
    }

    @Test
    void userFacingTextSanitizerContract() throws Exception
    {
        run("com.ruoyi.system.ai.support.UserFacingTextSanitizerContractTest");
    }

    @Test
    void cancellationRegistryRace() throws Exception
    {
        run("com.ruoyi.system.ai.task.EduAiTaskCancellationRegistryRaceTest");
    }

    @Test
    void taskEventStoreConcurrency() throws Exception
    {
        run("com.ruoyi.system.ai.task.EduAiTaskEventStoreConcurrencyTest");
    }

    @Test
    void taskProgressCallback() throws Exception
    {
        run("com.ruoyi.system.ai.task.EduAiTaskProgressCallbackTest");
    }

    @Test
    void taskRunnerRecovery() throws Exception
    {
        run("com.ruoyi.system.ai.task.EduAiTaskRunnerRecoveryTest");
    }

    @Test
    void taskServiceIdempotency() throws Exception
    {
        run("com.ruoyi.system.service.impl.EduAiTaskServiceIdempotencyTest");
    }

    @Test
    void profileConfirmationServiceContract() throws Exception
    {
        run("com.ruoyi.system.service.impl.EduProfileConfirmationServiceContractTest");
    }

    @Test
    void profilePracticeVerificationServiceContract() throws Exception
    {
        run("com.ruoyi.system.service.impl.ProfilePracticeVerificationServiceContractTest");
    }

    private void run(String className) throws Exception
    {
        try
        {
            Class<?> testClass = Class.forName(className);
            Method main = testClass.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception)
            {
                throw exception;
            }
            if (cause instanceof Error error)
            {
                throw error;
            }
            throw e;
        }
    }
}
