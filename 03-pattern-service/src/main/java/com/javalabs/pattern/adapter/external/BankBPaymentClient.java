package com.javalabs.pattern.adapter.external;

import org.springframework.stereotype.Component;

/**
 * Bank B'nin (simüle edilmiş) client'ı. Bank A'dan TAMAMEN FARKLI bir şekli var: tek bir payload objesi
 * alır, tutarı minor unit (kuruş) olarak ister, {@code authorize()} der (Bank A'daki {@code makePayment()}
 * değil). Gerçek entegrasyonlarda provider'lar arası bu kadar (hatta daha fazla) farklılık normaldir.
 */
@Component
public class BankBPaymentClient {

    public BankBResult authorize(BankBPayload payload) {
        return new BankBResult(true, "AUTH-" + payload.orderRef(), "Approved by Bank B");
    }
}
