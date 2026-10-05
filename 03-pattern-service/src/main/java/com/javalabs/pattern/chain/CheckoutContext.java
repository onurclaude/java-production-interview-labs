package com.javalabs.pattern.chain;

import java.math.BigDecimal;

/**
 * Checkout validasyon zincirinin her rule'a verdiği immutable bağlam. Gerçek bir sistemde burada
 * customer geçmişi, fraud skoru, stok seviyesi gibi veriler DB/external servislerden gelirdi; bu lab'da
 * deterministik ve tekrar üretilebilir olması için (section 40 ilkesiyle aynı mantık) bu sinyaller
 * doğrudan request üzerinden verilir.
 */
public record CheckoutContext(
        String orderId,
        String customerId,
        BigDecimal amount,
        boolean customerBlocked,
        boolean simulateFraud,
        boolean stockAvailable) {
}
