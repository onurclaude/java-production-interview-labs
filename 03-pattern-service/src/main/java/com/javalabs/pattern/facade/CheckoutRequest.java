package com.javalabs.pattern.facade;

import java.math.BigDecimal;

public record CheckoutRequest(String orderId, String customerId, BigDecimal amount) {
}
