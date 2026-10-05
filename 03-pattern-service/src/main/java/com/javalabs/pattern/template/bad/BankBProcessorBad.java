package com.javalabs.pattern.template.bad;

import com.javalabs.pattern.adapter.external.BankBPayload;
import com.javalabs.pattern.adapter.external.BankBPaymentClient;
import com.javalabs.pattern.adapter.external.BankBResult;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** {@code BankAProcessorBad} ile karşılaştırın: 1, 2 ve 5. adımlar KARAKTER KARAKTER aynı. */
@Component
public class BankBProcessorBad {

    private static final Logger log = LoggerFactory.getLogger(BankBProcessorBad.class);

    private final BankBPaymentClient bankBClient;

    public BankBProcessorBad(BankBPaymentClient bankBClient) {
        this.bankBClient = bankBClient;
    }

    public PaymentResult process(PaymentCommand command) {
        // 1) validate — BankAProcessorBad'deki ile BİREBİR AYNI (copy-paste).
        if (command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        // 2) prepare request (Bank B'ye özgü)
        long amountInMinorUnits = command.amount().multiply(BigDecimal.valueOf(100)).longValueExact();
        BankBPayload payload = new BankBPayload(amountInMinorUnits, command.orderId(), "TRY");
        // 3) call provider
        BankBResult response = bankBClient.authorize(payload);
        // 4) map response (Bank B'ye özgü)
        PaymentResult result = new PaymentResult(command.orderId(), "BANK_B", response.approved(),
                "Template Method BAD - duplicated orchestration (" + response.authCode() + ")");
        // 5) audit — BankAProcessorBad'deki ile BİREBİR AYNI (copy-paste).
        log.info("[BAD] Payment processed: orderId={} provider={} success={}", result.orderId(),
                result.provider(), result.success());
        return result;
    }
}
