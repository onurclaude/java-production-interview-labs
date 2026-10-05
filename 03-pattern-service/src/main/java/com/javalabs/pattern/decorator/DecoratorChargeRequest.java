package com.javalabs.pattern.decorator;

import java.math.BigDecimal;

public record DecoratorChargeRequest(String orderId, BigDecimal amount) {
}
