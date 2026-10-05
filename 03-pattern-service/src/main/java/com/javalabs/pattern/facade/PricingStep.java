package com.javalabs.pattern.facade;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PricingStep {

    private static final BigDecimal SERVICE_FEE_RATE = new BigDecimal("0.01");

    /** Deterministik bir servis ücreti ekler (%1) — gerçek bir pricing engine burada kampanya/vergi hesaplardı. */
    public BigDecimal calculateFinalAmount(CheckoutRequest request) {
        BigDecimal fee = request.amount().multiply(SERVICE_FEE_RATE);
        return request.amount().add(fee);
    }
}
