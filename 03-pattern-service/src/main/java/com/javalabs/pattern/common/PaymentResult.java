package com.javalabs.pattern.common;

public record PaymentResult(String orderId, String provider, boolean success, String message) {

    public static PaymentResult success(String orderId, String provider, String message) {
        return new PaymentResult(orderId, provider, true, message);
    }
}
