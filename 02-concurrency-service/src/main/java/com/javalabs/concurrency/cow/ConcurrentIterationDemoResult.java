package com.javalabs.concurrency.cow;

import java.util.List;

public record ConcurrentIterationDemoResult(
        List<String> iteratedSnapshot,
        String addedDuringIteration,
        List<String> currentFullListAfterIteration,
        boolean concurrentModificationExceptionThrown) {
}
