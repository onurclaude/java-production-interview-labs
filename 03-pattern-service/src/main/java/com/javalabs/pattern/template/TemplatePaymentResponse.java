package com.javalabs.pattern.template;

public record TemplatePaymentResponse(String lab, String orderId, String provider, boolean success, String lesson) {
}
