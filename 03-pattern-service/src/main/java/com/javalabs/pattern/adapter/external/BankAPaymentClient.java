package com.javalabs.pattern.adapter.external;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Bank A'nın (simüle edilmiş) resmi SDK/client'ı. Gerçek hayatta bu class bir 3rd-party kütüphaneden
 * gelirdi — biz onun API ŞEKLİNİ değiştiremeyiz, sadece ona UYMAK zorundayız.
 */
@Component
public class BankAPaymentClient {

    public BankAResponse makePayment(String merchantOrder, BigDecimal total, String currency) {
        return new BankAResponse("OK", "BANKA-REF-" + merchantOrder, "Payment approved by Bank A");
    }
}
