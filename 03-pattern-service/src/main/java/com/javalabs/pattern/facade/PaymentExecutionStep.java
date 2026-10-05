package com.javalabs.pattern.facade;

import com.javalabs.pattern.common.ProviderCallSimulator;
import com.javalabs.pattern.config.PatternLabProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentExecutionStep {

    private final ProviderCallSimulator providerCall;
    private final long providerDelayMs;

    public PaymentExecutionStep(ProviderCallSimulator providerCall, PatternLabProperties properties) {
        this.providerCall = providerCall;
        this.providerDelayMs = properties.providerDelayMs();
    }

    public boolean charge(String orderId, BigDecimal finalAmount) {
        providerCall.call(providerDelayMs);
        return true;
    }
}
