package com.javalabs.pattern.adapter.good;

import com.javalabs.pattern.adapter.PaymentProvider;
import com.javalabs.pattern.adapter.external.BankBPayload;
import com.javalabs.pattern.adapter.external.BankBPaymentClient;
import com.javalabs.pattern.adapter.external.BankBResult;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Bank B tutarı minor unit (kuruş) ister ve tek bir payload objesi alır — Bank A'dan TAMAMEN FARKLI bir
 * sözleşme. Ama bu farkı {@code AdapterGoodPaymentService} hiç görmez: adapter, {@code amount * 100}
 * dönüşümünü ve {@code BankBPayload} oluşturmayı burada, KENDİ İÇİNDE yapar.
 */
@Component
public class BankBPaymentAdapter implements PaymentProvider {

    private final BankBPaymentClient bankBClient;

    public BankBPaymentAdapter(BankBPaymentClient bankBClient) {
        this.bankBClient = bankBClient;
    }

    @Override
    public PaymentResult charge(PaymentCommand command) {
        long amountInMinorUnits = command.amount().multiply(BigDecimal.valueOf(100)).longValueExact();
        BankBPayload payload = new BankBPayload(amountInMinorUnits, command.orderId(), "TRY");
        BankBResult result = bankBClient.authorize(payload);
        return new PaymentResult(command.orderId(), providerName(), result.approved(),
                "GOOD: BankBPayload/BankBResult mapping stays inside the adapter (" + result.authCode() + ")");
    }

    @Override
    public String providerName() {
        return "BANK_B";
    }
}
