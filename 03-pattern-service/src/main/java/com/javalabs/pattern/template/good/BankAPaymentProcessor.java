package com.javalabs.pattern.template.good;

import com.javalabs.pattern.adapter.external.BankAPaymentClient;
import com.javalabs.pattern.adapter.external.BankAResponse;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.template.AbstractPaymentProcessor;
import org.springframework.stereotype.Component;

/**
 * Bu class'ta orkestrasyon (validate -> prepare -> call -> map -> audit SIRASI) YOKTUR — o
 * {@link AbstractPaymentProcessor#process} içinde bir kez yazılıdır. Burada SADECE Bank A'ya özgü 3 adım var.
 */
@Component
public class BankAPaymentProcessor extends AbstractPaymentProcessor<BankAProviderRequest, BankAResponse> {

    private final BankAPaymentClient bankAClient;

    public BankAPaymentProcessor(BankAPaymentClient bankAClient) {
        this.bankAClient = bankAClient;
    }

    @Override
    protected BankAProviderRequest prepareRequest(PaymentCommand command) {
        return new BankAProviderRequest(command.orderId(), command.amount(), "TRY");
    }

    @Override
    protected BankAResponse callProvider(BankAProviderRequest providerRequest) {
        return bankAClient.makePayment(providerRequest.merchantOrder(), providerRequest.total(),
                providerRequest.currency());
    }

    @Override
    protected PaymentResult mapResponse(PaymentCommand command, BankAResponse providerResponse) {
        boolean success = "OK".equals(providerResponse.statusCode());
        return new PaymentResult(command.orderId(), "BANK_A", success,
                "Template Method GOOD (" + providerResponse.referenceId() + ")");
    }
}
