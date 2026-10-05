package com.javalabs.pattern.builder;

import java.math.BigDecimal;

/** HTTP'den gelen basit alanlar; description/callbackUrl/installment/merchantReference opsiyoneldir (null geçilebilir). */
public record BuilderLabRequest(
        String orderId,
        String customerId,
        BigDecimal amount,
        String description,
        String callbackUrl,
        Integer installment,
        String merchantReference) {
}
