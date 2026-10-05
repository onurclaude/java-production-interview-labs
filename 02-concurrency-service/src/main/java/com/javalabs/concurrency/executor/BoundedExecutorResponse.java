package com.javalabs.concurrency.executor;

public record BoundedExecutorResponse(
        int taskCount,
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int accepted,
        int rejected,
        int completed,
        int maxObservedActiveWorkers,
        int maxObservedQueueSize,
        long elapsedMs,
        boolean finishedWithinTimeout,
        String note) {
}
