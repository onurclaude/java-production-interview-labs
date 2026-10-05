package com.javalabs.pattern.template;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * BUSINESS PROBLEM: Bank A ve Bank B için ödeme akışının BİR KISMI her zaman AYNIDIR: önce validate et,
 * sonra provider'a özel bir request hazırla, provider'ı çağır, cevabı bizim modelimize map'le, sonunda
 * audit/log at. Bu "iskelet" (adımların SIRASI ve VAR OLMASI) sabittir — değişen sadece HER ADIMIN
 * provider'a özgü detayıdır. Bunu base class olmadan yazarsak (bkz. {@code template.bad.*Processor}),
 * her provider processor'ı AYNI 5 adımlık orkestrasyon kodunu COPY-PASTE eder.
 *
 * <p>BU TEMPLATE METHOD NEYİ DEĞİŞTİRİYOR? {@link #process} METHOD'U {@code final}'dır: alt class'lar
 * adımların SIRASINI değiştiremez, sadece HANGİ provider-specific işin yapılacağını
 * ({@link #prepareRequest}, {@link #callProvider}, {@link #mapResponse}) tanımlar. "Algoritmanın iskeleti
 * sabit, bazı adımları alt sınıflar doldurur" — Template Method'un tanımı tam olarak budur.
 *
 * <p>INHERITANCE MALİYETİ — NEDEN HER YERDE KULLANMIYORUZ: Bu class'ın alt sınıfları (BankA/BankB
 * processor) bu base class'a SIKI SIKIYA bağlıdır (is-a ilişkisi, compile-time). Strategy (Lab 1) ise
 * COMPOSITION kullanır: {@code PaymentStrategy} implementation'ları birbirinden tamamen bağımsızdır,
 * runtime'da inject edilir, hiçbir ortak base class'a bağlı değildir. Template Method'u SADECE
 * "algoritmanın iskeleti gerçekten sabit ve paylaşılan" durumlarda kullanın — "bir davranışın tamamını
 * runtime'da değiştirmek" istiyorsanız Strategy çoğu zaman daha esnektir (bkz. README "Template Method
 * vs Strategy").
 *
 * @param <ProviderRequest>  bu processor'ın provider'a özgü, hazırlanmış request şekli
 * @param <ProviderResponse> provider'ın HAM (henüz bizim modelimize çevrilmemiş) cevabı
 */
public abstract class AbstractPaymentProcessor<ProviderRequest, ProviderResponse> {

    private static final Logger log = LoggerFactory.getLogger(AbstractPaymentProcessor.class);

    /**
     * İskelet SABİTTİR: adım sırası burada bir kez tanımlanır, alt class'lar bunu DEĞİŞTİREMEZ
     * ({@code final}). Her provider için AYNI 5 adım çalışır; değişen SADECE adımların İÇİNİN nasıl
     * doldurulduğudur.
     */
    public final PaymentResult process(PaymentCommand command) {
        validate(command);
        ProviderRequest providerRequest = prepareRequest(command);
        ProviderResponse providerResponse = callProvider(providerRequest);
        PaymentResult result = mapResponse(command, providerResponse);
        afterPayment(result);
        return result;
    }

    /** Ortak adım: her provider için aynı. Alt class bunu override ETMEZ (gerekirse genişletebilir ama bu lab'da sabit tutuyoruz). */
    protected void validate(PaymentCommand command) {
        if (command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }

    /** Değişken adım 1: bizim {@link PaymentCommand}'ımızı provider'ın istediği şekle çevir. */
    protected abstract ProviderRequest prepareRequest(PaymentCommand command);

    /** Değişken adım 2: provider'a özgü client'ı çağır (gerçekte bir network call olurdu). */
    protected abstract ProviderResponse callProvider(ProviderRequest providerRequest);

    /** Değişken adım 3: provider'ın HAM cevabını bizim {@link PaymentResult}'ımıza çevir. */
    protected abstract PaymentResult mapResponse(PaymentCommand command, ProviderResponse providerResponse);

    /** Ortak adım: audit/log. Her provider için aynı davranış; override edilebilir ama bu lab'da sabit. */
    protected void afterPayment(PaymentResult result) {
        log.info("[TEMPLATE] Payment processed: orderId={} provider={} success={}",
                result.orderId(), result.provider(), result.success());
    }
}
