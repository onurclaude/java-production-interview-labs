package com.javalabs.pattern.decorator;

import com.javalabs.pattern.adapter.PaymentProvider;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BUSINESS PROBLEM: Her {@link PaymentProvider} çağrısının etrafına logging eklemek istiyoruz. BAD
 * yaklaşım, bu logging kodunu HER provider implementation'ının (BankA, BankB, ileride BankC...) İÇİNE
 * copy-paste etmektir — aynı 2 satır log kodu N kez tekrarlanır.
 *
 * <p>BU DECORATOR NEYİ DEĞİŞTİRİYOR? Bu class da bir {@link PaymentProvider}'DIR (aynı contract'ı
 * implement eder) AMA kendi işini yapmaz — GERÇEK provider'ı ({@code delegate}) SARAR ve çağrıyı ona
 * DELEGE eder, öncesine/sonrasına log ekler. Asıl provider implementasyonu (örn.
 * {@code BankAPaymentAdapter}) HİÇ DEĞİŞMEDİ — logging, mevcut davranışın DIŞINDAN eklendi.
 *
 * <p>Compile-time'da {@code LoggingPaymentProviderDecorator} ile asıl adapter arasında hiçbir fark
 * YOKTUR (ikisi de {@code PaymentProvider}); çağıran taraf hangisiyle konuştuğunu bilmez/bilmesine
 * gerek yoktur.
 */
public class LoggingPaymentProviderDecorator implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentProviderDecorator.class);

    private final PaymentProvider delegate;

    public LoggingPaymentProviderDecorator(PaymentProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public PaymentResult charge(PaymentCommand command) {
        log.info("[DECORATOR:LOGGING] >>> charge START orderId={} amount={}", command.orderId(), command.amount());
        PaymentResult result = delegate.charge(command);
        log.info("[DECORATOR:LOGGING] <<< charge END orderId={} success={}", result.orderId(), result.success());
        return result;
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }
}
