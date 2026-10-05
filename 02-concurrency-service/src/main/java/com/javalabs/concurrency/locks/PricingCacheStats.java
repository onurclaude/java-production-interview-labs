package com.javalabs.concurrency.locks;

public record PricingCacheStats(int version, int currentReaders, int maxObservedConcurrentReaders, long totalReads,
                                 long totalReloads) {
}
