package com.javalabs.pattern.chain;

import java.math.BigDecimal;

public record ChainCheckoutRequest(
        String orderId,
        String customerId,
        BigDecimal amount,
        boolean customerBlocked,
        boolean simulateFraud,
        boolean stockAvailable) {
}
