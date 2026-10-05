package com.javalabs.pattern.strategy;

public record StrategyPaymentResponse(
        String lab,
        String orderId,
        String paymentType,
        String selectedStrategy,
        String provider,
        boolean success,
        String lesson) {
}
