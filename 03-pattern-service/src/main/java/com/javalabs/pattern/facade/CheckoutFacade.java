package com.javalabs.pattern.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * BUSINESS PROBLEM: Checkout tamamlamak için 5 alt sistem sırayla çalışmalı: validation, pricing,
 * payment, notification, audit. Bu çağrı sırasını CONTROLLER'a yazarsak (bkz.
 * {@code FacadeLabController.bad}), controller artık checkout'un İÇ MİMARİSİNİ (hangi alt sistem var,
 * hangi sırada çağrılır, hangisinin çıktısı diğerine girdi olur) bilmek zorunda kalır. Checkout akışı
 * değiştiğinde (örn. "pricing'den önce bir kampanya kontrolü ekleyelim") controller da değişir — oysa
 * controller'ın tek işi HTTP request/response çevirmek olmalı.
 *
 * <p>BU FACADE NEYİ DEĞİŞTİRİYOR? Facade'ın amacı "5 method çağırmak yerine 1 method çağırmak" (method
 * sayısını azaltmak) DEĞİLDİR. Amacı, çağıran tarafın (controller) alt sistemlerin VARLIĞINI VE
 * SIRASINI bilmesine gerek KALMAMASIDIR. Controller sadece {@code checkoutFacade.checkout(request)} der;
 * "içeride kaç adım var, hangi sırada" bilgisi TAMAMEN bu class'ın sorumluluğundadır.
 *
 * <p>GOD CLASS RİSKİ — ÇOK ÖNEMLİ: Bu class'ın business logic'in KENDİSİNİ (validasyon kuralları,
 * pricing hesaplaması, provider çağrısı detayları) içine ALMADIĞINA dikkat edin — her iş kendi
 * {@code *Step} class'ındadır, Facade sadece bunları SIRAYLA ÇAĞIRIR (orchestration). Eğer bu class
 * büyüyüp "pricing hesaplamasını da ben yapayım, validasyon kuralını da ben yazayım" derse, Facade
 * pattern'in amacını kaybeder ve bir God Service'e dönüşür.
 */
@Service
public class CheckoutFacade {

    private static final Logger log = LoggerFactory.getLogger(CheckoutFacade.class);

    private final CheckoutValidationStep validationStep;
    private final PricingStep pricingStep;
    private final PaymentExecutionStep paymentStep;
    private final NotificationStep notificationStep;
    private final AuditStep auditStep;

    public CheckoutFacade(CheckoutValidationStep validationStep, PricingStep pricingStep,
                          PaymentExecutionStep paymentStep, NotificationStep notificationStep,
                          AuditStep auditStep) {
        this.validationStep = validationStep;
        this.pricingStep = pricingStep;
        this.paymentStep = paymentStep;
        this.notificationStep = notificationStep;
        this.auditStep = auditStep;
    }

    public CheckoutResult checkout(CheckoutRequest request) {
        log.info("[FACADE] checkout started for order {}", request.orderId());

        validationStep.validate(request);
        BigDecimal finalAmount = pricingStep.calculateFinalAmount(request);
        boolean paymentSuccess = paymentStep.charge(request.orderId(), finalAmount);
        notificationStep.notifyCustomer(request.orderId());
        auditStep.record(request.orderId(), paymentSuccess);

        List<String> executedSteps = List.of("VALIDATION", "PRICING", "PAYMENT", "NOTIFICATION", "AUDIT");
        return new CheckoutResult("FACADE_GOOD", request.orderId(), finalAmount, paymentSuccess, executedSteps);
    }
}
