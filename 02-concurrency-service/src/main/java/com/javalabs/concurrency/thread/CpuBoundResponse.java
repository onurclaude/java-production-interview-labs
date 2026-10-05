package com.javalabs.concurrency.thread;

public record CpuBoundResponse(
        CpuBoundMode mode,
        int taskCount,
        int workUnits,
        int completedTaskCount,
        long elapsedMs,
        int availableProcessors,
        String note) {
}
