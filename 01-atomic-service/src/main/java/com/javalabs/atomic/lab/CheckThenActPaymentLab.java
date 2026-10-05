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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LAB 2 — BAD: "int yerine AtomicInteger kullandım, problem çözüldü" yanılgısı.
 *
 * <p>Bu sınıfta lost update ve visibility problemi YOK: sayaç her zaman doğru sayar ve yük bitince 0'a döner.
 * Buna rağmen business kuralı (provider'da aynı anda en fazla N request) yine ihlal edilir.
 * Yani "sayaç doğru" ile "limit doğru uygulanıyor" farklı şeylerdir.
 */
@Service
public class CheckThenActPaymentLab {

    private static final Logger log = LoggerFactory.getLogger(CheckThenActPaymentLab.class);

    private final PaymentProviderClient provider;
    private final LabMetrics metrics;
    private final int limit;
    private final Duration raceWindow;

    private final AtomicInteger activeRequests = new AtomicInteger();

    public CheckThenActPaymentLab(PaymentProviderClient provider, LabMetrics metrics, AtomicLabProperties properties) {
        this.provider = provider;
        this.metrics = metrics;
        this.limit = properties.providerConcurrencyLimit();
        this.raceWindow = properties.raceWindow();
    }

    public PaymentAttemptResponse pay(boolean fail) {
        long start = System.nanoTime();
        String paymentId = "PAY-" + metrics.recordRequest();

        /*
         * KRİTİK NOKTA — Atomic primitive kullanmak bütün business operation'ı otomatik olarak atomic hale getirmez.
         *
         * get() tek başına atomic'tir. incrementAndGet() tek başına atomic'tir.
         * Ama business operation "limit dolu değilse bir slot al"dır ve bu İKİ ayrı atomic çağrıdan oluşur.
         * İki çağrının arasında başka thread'ler araya girebilir:
         *
         *   Thread A: get() -> 19   (kontrol geçti)
         *   Thread B: get() -> 19   (kontrol geçti)
         *   Thread A: incrementAndGet() -> 20
         *   Thread B: incrementAndGet() -> 21   <- limit aşıldı, ama artık geri dönüş yok
         *
         * Atomiklik, korumak istediğimiz invariant'ı (active <= limit) kapsayan TÜM okuma+yazma adımlarını
         * tek bir bölünmez adımda yapmalıdır. Çözüm: compareAndSet (Lab 3).
         */
        int seen = activeRequests.get();
        if (seen >= limit) {
            metrics.recordRejected();
            log.info("REJECT {}: get()={} >= limit={}", paymentId, seen, limit);
            return PaymentAttemptResponse.rejected(LabImplementation.ATOMIC_CHECK_THEN_ACT, paymentId, limit, seen, null, start);
        }
        log.info("CHECK passed {}: get()={} (< {})", paymentId, seen, limit);

        // Bu bekleme production kodu değildir; race condition'ı deterministik şekilde görünür hale getirmek için lab amacıyla kullanılmıştır.
        LabOnlyDelay.widenRaceWindow(raceWindow);

        // incrementAndGet() kontrolü tekrar yapmaz; değer artık limitin üstünde olsa bile koşulsuz artırır.
        int afterIncrement = activeRequests.incrementAndGet();
        if (afterIncrement > limit) {
            log.warn("LIMIT VIOLATED {}: get() saw {}, incrementAndGet() returned {} > limit={}", paymentId, seen, afterIncrement, limit);
        } else {
            log.info("ACT {}: get() saw {}, incrementAndGet() returned {}", paymentId, seen, afterIncrement);
        }
        metrics.recordAccepted();

        try {
            ProviderChargeResult result = provider.charge(paymentId, fail);
            return PaymentAttemptResponse.accepted(LabImplementation.ATOMIC_CHECK_THEN_ACT, paymentId, limit, seen, null, result, start);
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    public int activeRequests() {
        return activeRequests.get();
    }

    void reset() {
        activeRequests.set(0);
    }
}
