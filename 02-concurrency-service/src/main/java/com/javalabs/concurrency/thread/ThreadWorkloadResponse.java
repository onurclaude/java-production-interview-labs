package com.javalabs.concurrency.thread;

import com.javalabs.concurrency.common.ThreadModel;

public record ThreadWorkloadResponse(
        ThreadModel implementation,
        int taskCount,
        int completedTaskCount,
        long elapsedMs,
        boolean allTasksObservedVirtual,
        int maxObservedConcurrency,
        String note) {
}
