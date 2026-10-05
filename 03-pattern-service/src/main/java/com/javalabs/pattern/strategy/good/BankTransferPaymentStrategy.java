package com.javalabs.pattern.strategy.good;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;
import com.javalabs.pattern.common.ProviderCallSimulator;
import com.javalabs.pattern.config.PatternLabProperties;
import com.javalabs.pattern.strategy.PaymentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BankTransferPaymentStrategy implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(BankTransferPaymentStrategy.class);

    private final ProviderCallSimulator providerCall;
    private final long providerDelayMs;

    public BankTransferPaymentStrategy(ProviderCallSimulator providerCall, PatternLabProperties properties) {
        this.providerCall = providerCall;
        this.providerDelayMs = properties.providerDelayMs();
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        log.info("[GOOD] BankTransferPaymentStrategy initiating transfer for {}", command.orderId());
        providerCall.call(providerDelayMs);
        return PaymentResult.success(command.orderId(), "BANK_TRANSFER_SIMULATOR",
                "Transfer initiated (GOOD: only this class knows bank-transfer-specific flow)");
    }

    @Override
    public PaymentType supports() {
        return PaymentType.BANK_TRANSFER;
    }
}
