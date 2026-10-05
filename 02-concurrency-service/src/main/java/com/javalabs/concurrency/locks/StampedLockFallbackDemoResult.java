package com.javalabs.concurrency.locks;

import java.math.BigDecimal;

/**
 * optimisticStillValidAfterWindow her zaman false olmalıdır: demo endpoint'i, optimistic read ile
 * validate() arasına bilinçli olarak gerçek bir write (reload) yerleştirir.
 */
public record StampedLockFallbackDemoResult(
        String productId,
        BigDecimal optimisticallyReadValue,
        boolean optimisticStillValidAfterWindow,
        BigDecimal fallbackReadValue) {
}
