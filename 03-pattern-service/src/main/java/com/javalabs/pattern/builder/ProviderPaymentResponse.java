package com.javalabs.pattern.builder;

import java.math.BigDecimal;
import java.util.Map;

public record ProviderPaymentResponse(
        String lab,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String description,
        String callbackUrl,
        int installment,
        String merchantReference,
        Map<String, String> metadata) {
}
