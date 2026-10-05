package com.javalabs.pattern.facade;

import com.javalabs.pattern.common.PatternLabValidation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CheckoutValidationStep {

    public void validate(CheckoutRequest request) {
        PatternLabValidation.requireNonBlank("orderId", request.orderId());
        PatternLabValidation.requireNonBlank("customerId", request.customerId());
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
