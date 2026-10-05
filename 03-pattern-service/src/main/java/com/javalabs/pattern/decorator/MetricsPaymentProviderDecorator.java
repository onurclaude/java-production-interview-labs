package com.javalabs.pattern.decorator;

import com.javalabs.pattern.adapter.PaymentProvider;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tıpkı {@link LoggingPaymentProviderDecorator} gibi: asıl provider'ı sarar, bu kez logging değil
 * SÜRE/SAYAÇ ölçümü ekler. İki decorator BİRBİRİNDEN BAĞIMSIZDIR ve İSTENEN SIRADA zincirlenebilir —
 * {@code new LoggingDecorator(new MetricsDecorator(bankAAdapter))} dediğinizde çağrı sırası:
 * Logging.charge() -> Metrics.charge() -> gerçek adapter.charge() -> geri sarmalı dönüş.
 */
public class MetricsPaymentProviderDecorator implements PaymentProvider {

    private final PaymentProvider delegate;
    private final AtomicLong callCount = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();

    public MetricsPaymentProviderDecorator(PaymentProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public PaymentResult charge(PaymentCommand command) {
        long startNanos = System.nanoTime();
        try {
            return delegate.charge(command);
        } finally {
            callCount.incrementAndGet();
            totalMs.addAndGet((System.nanoTime() - startNanos) / 1_000_000);
        }
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }

    public long callCount() {
        return callCount.get();
    }

    public long totalMs() {
        return totalMs.get();
    }
}
