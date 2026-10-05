package com.javalabs.concurrency.aba;

public record AbaBadResult(
        String before,
        String after,
        boolean referenceUnchanged,
        boolean bugDemonstrated,
        long totalRuns,
        long bugTriggeredCount,
        String note) {
}
