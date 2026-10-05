package com.javalabs.concurrency.coordination;

public record CountDownLatchResponse(
        boolean completedInTime,
        long remainingCount,
        boolean customerCheckDone,
        boolean fraudCheckDone,
        boolean pricingCheckDone,
        long elapsedMs,
        String note) {
}
