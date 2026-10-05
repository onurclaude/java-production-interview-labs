package com.javalabs.concurrency.locks;

public record StampedLockStats(
        int version,
        long optimisticReadAttempts,
        long optimisticReadSuccess,
        long optimisticReadFallback,
        long totalReloads) {
}
