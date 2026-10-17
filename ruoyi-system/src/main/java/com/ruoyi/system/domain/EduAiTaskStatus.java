package com.ruoyi.system.domain;

import java.util.Set;

/** Lifecycle states for a durable AI chat task. */
public final class EduAiTaskStatus
{
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> TERMINAL = Set.of(SUCCEEDED, FAILED, CANCELLED);

    private EduAiTaskStatus()
    {
    }

    public static boolean isTerminal(String status)
    {
        return TERMINAL.contains(status);
    }
}
