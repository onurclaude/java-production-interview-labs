package com.javalabs.pattern.adapter;

import java.math.BigDecimal;

/** provider: "BANK_A" veya "BANK_B" (sadece GOOD endpoint'inde kullanılır; BAD her zaman Bank A'ya gider). */
public record AdapterChargeRequest(String orderId, BigDecimal amount, String provider) {
}
