package com.javalabs.atomic.lab;

import com.javalabs.atomic.config.AtomicLabProperties;
import com.javalabs.atomic.dto.LabImplementation;
import com.javalabs.atomic.dto.PaymentAttemptResponse;
import com.javalabs.atomic.metrics.LabMetrics;
import com.javalabs.atomic.provider.PaymentProviderClient;
import com.javalabs.atomic.provider.ProviderChargeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LAB 3 — GOOD (JVM-local): slot rezervasyonu compareAndSet ile atomik.
 *
 * <p>Aynı JVM içinde provider'a giden eşzamanlı request sayısı limit'i asla aşmaz.
 * Multi-instance ortamında neden yetmediği için bkz. {@link CasSlotLimiter}.
 */
@Service
public class CasPaymentLab {

    private static final Logger log = LoggerFactory.getLogger(CasPaymentLab.class);

    private final PaymentProviderClient provider;
    private final LabMetrics metrics;
    private final CasSlotLimiter limiter;

    public CasPaymentLab(PaymentProviderClient provider, LabMetrics metrics, AtomicLabProperties properties) {
        this.provider = provider;
        this.metrics = metrics;
        this.limiter = new CasSlotLimiter("cas", properties.providerConcurrencyLimit(), properties.casRaceWindow(), metrics);
    }

    public PaymentAttemptResponse pay(boolean fail) {
        long start = System.nanoTime();
        String paymentId = "PAY-" + metrics.recordRequest();

        SlotReservation reservation = limiter.tryReserve();
        if (!reservation.reserved()) {
            metrics.recordRejected();
            log.info("REJECT {}: active={} >= limit={} (casRetries={})",
                    paymentId, reservation.observedActive(), limiter.limit(), reservation.casRetries());
            return PaymentAttemptResponse.rejected(LabImplementation.CAS, paymentId, limiter.limit(),
                    reservation.observedActive(), reservation.casRetries(), start);
        }
        log.info("SLOT RESERVED {}: CAS({} -> {}) succeeded after {} failed attempts",
                paymentId, reservation.observedActive(), reservation.observedActive() + 1, reservation.casRetries());
        metrics.recordAccepted();

        // Reserve try bloğunun DIŞINDA: reddedilen request finally'ye hiç girmez, dolayısıyla release etmez.
        try {
            ProviderChargeResult result = provider.charge(paymentId, fail);
            return PaymentAttemptResponse.accepted(LabImplementation.CAS, paymentId, limiter.limit(),
                    reservation.observedActive(), reservation.casRetries(), result, start);
        } finally {
            limiter.release();
        }
    }

    public int activeRequests() {
        return limiter.activeRequests();
    }

    void reset() {
        limiter.reset();
    }
}
