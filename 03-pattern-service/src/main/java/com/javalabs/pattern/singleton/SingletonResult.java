package com.javalabs.pattern.singleton;

import java.math.BigDecimal;

public record SingletonResult(
        String requestOrderId,
        String observedOrderId,
        BigDecimal observedAmount,
        boolean corrupted,
        String thread) {
}
