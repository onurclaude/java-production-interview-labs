package com.javalabs.pattern.singleton;

import java.math.BigDecimal;

/** holdMs: field'a yazma ile okuma arasındaki (BAD) / local variable kullanımı sırasındaki (GOOD) LAB ONLY bekleme. */
public record SingletonRequest(String orderId, BigDecimal amount, long holdMs) {
}
