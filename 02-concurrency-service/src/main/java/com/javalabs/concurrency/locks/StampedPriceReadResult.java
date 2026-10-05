package com.javalabs.concurrency.locks;

import java.math.BigDecimal;

public record StampedPriceReadResult(String productId, BigDecimal price, int cacheVersion,
                                      boolean optimisticReadSucceeded) {
}
