package com.javalabs.concurrency.admin;

import com.javalabs.concurrency.aba.AbaBadStats;
import com.javalabs.concurrency.aba.AbaStampedStats;
import com.javalabs.concurrency.locks.PricingCacheStats;
import com.javalabs.concurrency.locks.StampedLockStats;
import com.javalabs.concurrency.provider.FraudProviderStats;

public record ConcurrencyStatsResponse(
        String instance,
        int customerCheckObservedMaxConcurrency,
        FraudProviderStats fraudProvider,
        int semaphorePermitsAvailable,
        PricingCacheStats readWriteLockCache,
        StampedLockStats stampedLockCache,
        AbaBadStats abaBad,
        AbaStampedStats abaStamped,
        int listenerRegistrySize,
        int featureRuleRegistrySize,
        String note) {
}
