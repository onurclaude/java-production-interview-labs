package com.javalabs.pattern.strategy;

import com.javalabs.pattern.common.PaymentType;

import java.math.BigDecimal;

public record StrategyPaymentRequest(String orderId, BigDecimal amount, PaymentType paymentType) {
}
