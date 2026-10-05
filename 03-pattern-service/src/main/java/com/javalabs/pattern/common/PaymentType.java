package com.javalabs.pattern.common;

/**
 * Checkout'un desteklediği ödeme yöntemleri. Strategy lab'ının (Lab 1) BAD ve GOOD implementasyonları
 * aynı enum'u kullanır — fark enum'da değil, "bu type'a göre davranışı KİM biliyor" sorusunun cevabındadır.
 */
public enum PaymentType {
    CREDIT_CARD,
    WALLET,
    BANK_TRANSFER
}
