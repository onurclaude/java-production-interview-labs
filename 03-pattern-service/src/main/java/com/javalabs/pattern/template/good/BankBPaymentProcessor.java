package com.javalabs.pattern.template.good;

import com.javalabs.pattern.adapter.external.BankBPayload;
import com.javalabs.pattern.adapter.external.BankBPaymentClient;
import com.javalabs.pattern.adapter.external.BankBResult;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.template.AbstractPaymentProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Bank B'nin request şekli Bank A'dan tamamen farklı ({@link BankBPayload}, minor unit tutar) ama
 * {@link AbstractPaymentProcessor#process} İSKELETİ birebir AYNIDIR — bu sınıf da aynı 5 adımdan geçer,
 * sadece 3 değişken adımın İÇİ Bank B'ye özgüdür.
 */
@Component
public class BankBPaymentProcessor extends AbstractPaymentProcessor<BankBPayload, BankBResult> {

    private final BankBPaymentClient bankBClient;

    public BankBPaymentProcessor(BankBPaymentClient bankBClient) {
        this.bankBClient = bankBClient;
    }

    @Override
    protected BankBPayload prepareRequest(PaymentCommand command) {
        long amountInMinorUnits = command.amount().multiply(BigDecimal.valueOf(100)).longValueExact();
        return new BankBPayload(amountInMinorUnits, command.orderId(), "TRY");
    }

    @Override
    protected BankBResult callProvider(BankBPayload providerRequest) {
        return bankBClient.authorize(providerRequest);
    }

    @Override
    protected PaymentResult mapResponse(PaymentCommand command, BankBResult providerResponse) {
        return new PaymentResult(command.orderId(), "BANK_B", providerResponse.approved(),
                "Template Method GOOD (" + providerResponse.authCode() + ")");
    }
}
