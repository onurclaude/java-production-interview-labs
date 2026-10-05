package com.javalabs.pattern.observer;

import com.javalabs.pattern.common.PaymentType;

import java.math.BigDecimal;

public record ObserverPaymentRequest(String orderId, BigDecimal amount, PaymentType paymentType) {
}
