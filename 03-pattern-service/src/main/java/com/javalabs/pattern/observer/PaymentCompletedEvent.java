package com.javalabs.pattern.observer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Spring'in {@code ApplicationEventPublisher}'ı, {@code ApplicationEvent}'ten türemeyen DÜZ bir POJO/record'u
 * da event olarak publish edebilir (Spring 4.2+). Bu record, "ödeme tamamlandı" OLGUSUNU taşır —
 * kim/kaç listener dinleyecek, bu event'in HİÇ bilmediği bir şeydir.
 */
public record PaymentCompletedEvent(String orderId, BigDecimal amount, String paymentType, Instant completedAt) {
}
