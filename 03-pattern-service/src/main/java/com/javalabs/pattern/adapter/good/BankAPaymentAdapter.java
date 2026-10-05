package com.javalabs.pattern.adapter.good;

import com.javalabs.pattern.adapter.PaymentProvider;
import com.javalabs.pattern.adapter.external.BankAPaymentClient;
import com.javalabs.pattern.adapter.external.BankAResponse;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.springframework.stereotype.Component;

/**
 * {@link BankAResponse} SADECE bu class'ın içinde yaşar — bu dosyanın DIŞINDA hiçbir yerde
 * {@code BankAResponse} import edilmez (bkz. README Acceptance — Adapter). Bu adapter'ın TEK işi:
 * bizim {@link PaymentCommand}'ımızı Bank A'nın istediği 3 parametreye çevirmek, ve Bank A'nın
 * {@code statusCode} string'ini bizim {@code boolean success}'imize çevirmek.
 */
@Component
public class BankAPaymentAdapter implements PaymentProvider {

    private final BankAPaymentClient bankAClient;

    public BankAPaymentAdapter(BankAPaymentClient bankAClient) {
        this.bankAClient = bankAClient;
    }

    @Override
    public PaymentResult charge(PaymentCommand command) {
        BankAResponse response = bankAClient.makePayment(command.orderId(), command.amount(), "TRY");
        boolean success = "OK".equals(response.statusCode());
        return new PaymentResult(command.orderId(), providerName(), success,
                "GOOD: BankAResponse mapping stays inside the adapter (" + response.referenceId() + ")");
    }

    @Override
    public String providerName() {
        return "BANK_A";
    }
}
