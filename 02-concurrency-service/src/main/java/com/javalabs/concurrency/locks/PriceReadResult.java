package com.javalabs.concurrency.locks;

import java.math.BigDecimal;

public record PriceReadResult(String productId, BigDecimal price, int cacheVersion, int concurrentReadersAtAccess) {
}
