package com.javalabs.pattern.adapter.bad;

import com.javalabs.pattern.adapter.external.BankAPaymentClient;
import com.javalabs.pattern.adapter.external.BankAResponse;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lab 3 — BAD: business service, Bank A'nın SDK'sının ŞEKLİNİ doğrudan biliyor.
 *
 * <p>BUSINESS PROBLEM: Ödeme almamız gerekiyor, provider Bank A.
 *
 * <p>BAD YAKLAŞIM: Bu class {@link BankAPaymentClient}'ı DOĞRUDAN çağırıyor ve dönen
 * {@link BankAResponse}'u KENDİSİ yorumluyor ({@code statusCode().equals("OK")}). Bunun anlamı: bu
 * business service artık "Bank A'nın status code'u string 'OK' ise başarılıdır" bilgisini TAŞIYOR.
 * Yarın Bank B eklenirse (farklı shape: {@code BankBResult.approved()} boolean), bu service'in
 * {@code pay()} method'u YENİ bir if/else ile BankB'nin ŞEKLİNİ de öğrenmek zorunda kalacak. Provider
 * SDK'sı güncellenip {@code statusCode} alanı kaldırılsa (örn. yeni SDK major version), bu değişiklik
 * DOĞRUDAN business kodunu kırar — çünkü business kod external modelin İÇİNE bakıyor.
 */
@Service
public class AdapterBadPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AdapterBadPaymentService.class);

    private final BankAPaymentClient bankAClient;

    public AdapterBadPaymentService(BankAPaymentClient bankAClient) {
        this.bankAClient = bankAClient;
    }

    public PaymentResult pay(PaymentCommand command) {
        // BAD: business service, Bank A'nın request şeklini (3 ayrı parametre, "TRY" literal) VE
        // response şeklini (statusCode string karşılaştırması) birebir biliyor.
        BankAResponse response = bankAClient.makePayment(command.orderId(), command.amount(), "TRY");
        boolean success = "OK".equals(response.statusCode());
        log.info("[BAD] AdapterBadPaymentService directly interpreted BankAResponse.statusCode={}",
                response.statusCode());
        return new PaymentResult(command.orderId(), "BANK_A",
                success, "BAD: service knows BankAResponse shape (" + response.referenceId() + ")");
    }
}
