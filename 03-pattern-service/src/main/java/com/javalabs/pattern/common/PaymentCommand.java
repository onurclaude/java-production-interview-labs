package com.javalabs.pattern.common;

import java.math.BigDecimal;

/**
 * "Bu ödemeyi yap" isteğini taşıyan immutable command. Birden fazla lab (Strategy, Singleton) bunu
 * kullanır: request-specific veri her zaman BÖYLE bir parametre/local variable olarak taşınmalı,
 * bir service bean'inin FIELD'ında DEĞİL (bkz. Lab 9 — Singleton).
 */
public record PaymentCommand(String orderId, BigDecimal amount, PaymentType paymentType) {
}
