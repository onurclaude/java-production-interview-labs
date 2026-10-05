package com.javalabs.pattern.template;

import java.math.BigDecimal;

public record TemplatePaymentRequest(String orderId, BigDecimal amount) {
}
