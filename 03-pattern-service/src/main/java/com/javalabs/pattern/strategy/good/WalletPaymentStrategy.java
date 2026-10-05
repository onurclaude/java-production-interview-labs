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

import java.math.BigDecimal;

/**
 * Wallet'a özgü bakiye kuralı SADECE burada yaşar. {@code CreditCardPaymentStrategy}'nin kart validasyon
 * kuralını hiç bilmez, bilmesine de gerek yoktur — her strategy kendi payment type'ının business
 * kurallarından sorumludur (Single Responsibility, payment type bazında).
 */
@Component
public class WalletPaymentStrategy implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(WalletPaymentStrategy.class);
    private static final BigDecimal WALLET_MAX_AMOUNT = new BigDecimal("10000");

    private final ProviderCallSimulator providerCall;
    private final long providerDelayMs;

    public WalletPaymentStrategy(ProviderCallSimulator providerCall, PatternLabProperties properties) {
        this.providerCall = providerCall;
        this.providerDelayMs = properties.providerDelayMs();
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        if (command.amount().compareTo(WALLET_MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Wallet payments above " + WALLET_MAX_AMOUNT + " are not allowed");
        }
        log.info("[GOOD] WalletPaymentStrategy checking balance + debiting for {}", command.orderId());
        providerCall.call(providerDelayMs);
        return PaymentResult.success(command.orderId(), "WALLET_LEDGER_SIMULATOR",
                "Wallet debited (GOOD: only this class knows wallet-specific rules)");
    }

    @Override
    public PaymentType supports() {
        return PaymentType.WALLET;
    }
}
