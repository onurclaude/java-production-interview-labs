package com.javalabs.concurrency.aba;

public record AbaStampedResult(
        String before,
        String after,
        int stampBefore,
        int stampAfter,
        boolean referenceUnchanged,
        boolean reallyUnchanged,
        boolean bugPrevented,
        long totalRuns,
        long bugPreventedCount,
        String note) {
}
