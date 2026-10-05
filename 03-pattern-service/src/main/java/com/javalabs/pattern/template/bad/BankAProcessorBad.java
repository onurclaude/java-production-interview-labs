package com.javalabs.pattern.template.bad;

import com.javalabs.pattern.adapter.external.BankAPaymentClient;
import com.javalabs.pattern.adapter.external.BankAResponse;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Lab 4 — BAD: orkestrasyon (validate -> prepare -> call -> map -> audit) bu class'ın İÇİNDE, baştan
 * yazılmış. {@code BankBProcessorBad}'a bakın: orkestrasyon SIRASI (5 adım, aynı sıra) BİREBİR AYNI,
 * sadece provider-specific detaylar farklı. Bu COPY-PASTE'dir: ortak akışta bir hata/iyileştirme
 * bulunursa (örn. audit log formatı değişecek), HER processor'da TEK TEK düzeltilmesi gerekir.
 */
@Component
public class BankAProcessorBad {

    private static final Logger log = LoggerFactory.getLogger(BankAProcessorBad.class);

    private final BankAPaymentClient bankAClient;

    public BankAProcessorBad(BankAPaymentClient bankAClient) {
        this.bankAClient = bankAClient;
    }

    public PaymentResult process(PaymentCommand command) {
        // 1) validate — BankBProcessorBad'de de BİREBİR AYNI kod tekrar yazılı.
        if (command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        // 2) prepare request (Bank A'ya özgü)
        String merchantOrder = command.orderId();
        BigDecimal total = command.amount();
        // 3) call provider
        BankAResponse response = bankAClient.makePayment(merchantOrder, total, "TRY");
        // 4) map response (Bank A'ya özgü)
        boolean success = "OK".equals(response.statusCode());
        PaymentResult result = new PaymentResult(command.orderId(), "BANK_A", success,
                "Template Method BAD - duplicated orchestration (" + response.referenceId() + ")");
        // 5) audit — BankBProcessorBad'de de BİREBİR AYNI log satırı tekrar yazılı.
        log.info("[BAD] Payment processed: orderId={} provider={} success={}", result.orderId(),
                result.provider(), result.success());
        return result;
    }
}
