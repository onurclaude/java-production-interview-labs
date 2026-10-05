package com.javalabs.pattern.decorator;

public record DecoratorChargeResponse(String lab, String orderId, boolean success, long observedCallCount,
                                       long observedTotalMs, String chain) {
}
