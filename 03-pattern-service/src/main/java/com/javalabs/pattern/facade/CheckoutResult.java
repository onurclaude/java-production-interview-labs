package com.javalabs.pattern.facade;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutResult(
        String lab,
        String orderId,
        BigDecimal finalAmount,
        boolean paymentSuccess,
        List<String> executedSteps) {
}
