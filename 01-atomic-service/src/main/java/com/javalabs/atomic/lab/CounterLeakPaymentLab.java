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

/**
 * LAB 4 — Counter leak.
 *
 * <p>İki implementasyon da slot'u AYNI doğru CAS mekanizmasıyla alır; tek fark release'in nerede yapıldığıdır.
 * Böylece bug'ın kaynağı izole edilir: concurrency değil, exception path.
 * BAD ve GOOD ayrı sayaç kullanır; BAD'in sızdırdığı slot'lar GOOD'u etkilemez.
 */
@Service
public class CounterLeakPaymentLab {

    private static final Logger log = LoggerFactory.getLogger(CounterLeakPaymentLab.class);

    private final PaymentProviderClient provider;
    private final LabMetrics metrics;

    // Leak lab'ında race penceresine ihtiyaç yok; konu eşzamanlılık değil exception akışı.
    private final CasSlotLimiter badLimiter;
    private final CasSlotLimiter goodLimiter;

    public CounterLeakPaymentLab(PaymentProviderClient provider, LabMetrics metrics, AtomicLabProperties properties) {
        this.provider = provider;
        this.metrics = metrics;
        this.badLimiter = new CasSlotLimiter("leak-bad", properties.providerConcurrencyLimit(), Duration.ZERO, metrics);
        this.goodLimiter = new CasSlotLimiter("leak-good", properties.providerConcurrencyLimit(), Duration.ZERO, metrics);
    }

    /**
     * BAD: release "mutlu yolun" sonunda.
     *
     * Provider exception atarsa release satırına hiç gelinmez; slot sonsuza kadar dolu kalır.
     * Her başarısız ödeme bir slot sızdırır. Provider'da gerçekte hiç aktif request yokken sayaç limite
     * ulaşır ve servis tüm yeni ödemeleri "limit dolu" diye reddetmeye başlar. Dışarıdan bakınca
     * provider sağlıklı, uygulama sağlıklı görünür ama hiçbir ödeme geçmez. Tipik olarak sadece pod
     * restart'ı düzeltir (sayaç heap'te sıfırlanır) ve bug restart'lar arasında tekrar birikir.
     */
    public PaymentAttemptResponse payBad(boolean fail) {
        long start = System.nanoTime();
        String paymentId = "PAY-" + metrics.recordRequest();

        SlotReservation reservation = badLimiter.tryReserve();
        if (!reservation.reserved()) {
            metrics.recordRejected();
            log.warn("REJECT {} (leak-bad): counter says {} >= limit={} -> is the provider really busy? (check provider.currentInFlight)",
                    paymentId, reservation.observedActive(), badLimiter.limit());
            return PaymentAttemptResponse.rejected(LabImplementation.LEAK_BAD, paymentId, badLimiter.limit(),
                    reservation.observedActive(), null, start);
        }
        metrics.recordAccepted();

        ProviderChargeResult result = provider.charge(paymentId, fail);
        // BUG: Exception durumunda bu satır çalışmaz -> slot sızar.
        badLimiter.release();

        return PaymentAttemptResponse.accepted(LabImplementation.LEAK_BAD, paymentId, badLimiter.limit(),
                reservation.observedActive(), null, result, start);
    }

    /**
     * GOOD: reserve try'ın dışında, release finally'de.
     *
     * finally neden gerekli? Provider çağrısı sadece "başarılı" veya "exception" ile bitmez; timeout,
     * interrupt, beklenmeyen RuntimeException da olabilir. Hangi yoldan çıkılırsa çıkılsın alınan slot
     * geri verilmelidir. finally bunu dilin kendisi seviyesinde garanti eder.
     *
     * Neden reserve try'ın içinde değil? Yaygın ikinci bug şudur:
     *
     *   try {
     *       if (!tryReserve()) return rejected;   // reddedildi...
     *       provider.charge(...);
     *   } finally {
     *       release();                            // ...ama yine de release ediyor!
     *   }
     *
     * Reddedilen her request hiç almadığı bir slot'u geri verir; sayaç negatife kayar ve limit sessizce
     * genişler. Kural: sadece gerçekten aldığın kaynağı serbest bırak. Ek emniyet olarak
     * CasSlotLimiter.release() sayacın 0'ın altına inmesine izin vermez.
     */
    public PaymentAttemptResponse payGood(boolean fail) {
        long start = System.nanoTime();
        String paymentId = "PAY-" + metrics.recordRequest();

        SlotReservation reservation = goodLimiter.tryReserve();
        if (!reservation.reserved()) {
            metrics.recordRejected();
            log.info("REJECT {} (leak-good): active={} >= limit={}", paymentId, reservation.observedActive(), goodLimiter.limit());
            return PaymentAttemptResponse.rejected(LabImplementation.LEAK_GOOD, paymentId, goodLimiter.limit(),
                    reservation.observedActive(), null, start);
        }
        metrics.recordAccepted();

        try {
            ProviderChargeResult result = provider.charge(paymentId, fail);
            return PaymentAttemptResponse.accepted(LabImplementation.LEAK_GOOD, paymentId, goodLimiter.limit(),
                    reservation.observedActive(), null, result, start);
        } finally {
            goodLimiter.release();
        }
    }

    public int badActiveRequests() {
        return badLimiter.activeRequests();
    }

    public int goodActiveRequests() {
        return goodLimiter.activeRequests();
    }

    void reset() {
        badLimiter.reset();
        goodLimiter.reset();
    }
}
