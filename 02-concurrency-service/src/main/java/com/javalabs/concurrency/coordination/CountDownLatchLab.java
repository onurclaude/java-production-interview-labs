package com.javalabs.concurrency.coordination;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lab 7 — CountDownLatch.
 *
 * <p>Gerçek senaryo: bir order'ı işlemeye devam etmeden önce üç BAĞIMSIZ kontrolün (Customer, Fraud,
 * Pricing) hepsinin bitmesini beklememiz gerekiyor. Bu kontroller birbirinden habersiz, paralel
 * çalışabilir — CountDownLatch(3) tam olarak bu "N bağımsız olayın tamamlanmasını bekle" problemini çözer.
 *
 * <p>CountDownLatch ONE-SHOT'tur: count sıfıra indikten sonra RESET EDİLEMEZ. Yeniden kullanmak isterseniz
 * (örn. bir sonraki order için) yeni bir CountDownLatch oluşturmanız gerekir — bu yüzden her çağrı kendi
 * latch'ini yaratır. Bunu CyclicBarrier ile karşılaştırın (Lab 8): o, aynı instance üzerinde tekrar tekrar
 * kullanılabilir (reusable).
 */
@Service
public class CountDownLatchLab {

    private static final Logger log = LoggerFactory.getLogger(CountDownLatchLab.class);

    private final ConcurrencyLabProperties.Safety safety;

    public CountDownLatchLab(ConcurrencyLabProperties properties) {
        this.safety = properties.safety();
    }

    public CountDownLatchResponse run(long customerDelayMs, long fraudDelayMs, long pricingDelayMs,
                                       boolean simulateCrashWithoutFinally, long awaitTimeoutMs) {
        LabInputValidation.requireRange("customerCheckDelayMs", customerDelayMs, 0, safety.maxDelayMs());
        LabInputValidation.requireRange("fraudCheckDelayMs", fraudDelayMs, 0, safety.maxDelayMs());
        LabInputValidation.requireRange("pricingCheckDelayMs", pricingDelayMs, 0, safety.maxDelayMs());
        LabInputValidation.requireRange("awaitTimeoutMs", awaitTimeoutMs, 100, 30_000);

        log.info("CountDownLatch lab starting: customerDelay={} fraudDelay={} pricingDelay={} " +
                        "simulateCrashWithoutFinally={} awaitTimeoutMs={}",
                customerDelayMs, fraudDelayMs, pricingDelayMs, simulateCrashWithoutFinally, awaitTimeoutMs);

        CountDownLatch latch = new CountDownLatch(3);
        AtomicBoolean customerDone = new AtomicBoolean(false);
        AtomicBoolean fraudDone = new AtomicBoolean(false);
        AtomicBoolean pricingDone = new AtomicBoolean(false);

        long startNanos = System.nanoTime();
        Thread.ofVirtual().name("latch-customer-check").start(
                () -> safeWorker("CustomerCheck", customerDelayMs, latch, customerDone));
        Thread.ofVirtual().name("latch-fraud-check").start(() -> {
            if (simulateCrashWithoutFinally) {
                unsafeWorker("FraudCheck", fraudDelayMs, latch);
            } else {
                safeWorker("FraudCheck", fraudDelayMs, latch, fraudDone);
            }
        });
        Thread.ofVirtual().name("latch-pricing-check").start(
                () -> safeWorker("PricingCheck", pricingDelayMs, latch, pricingDone));

        boolean completedInTime = awaitLatch(latch, awaitTimeoutMs);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        long remaining = latch.getCount();

        log.info("CountDownLatch lab finished: completedInTime={} remainingCount={} elapsedMs={}",
                completedInTime, remaining, elapsedMs);

        String note = completedInTime
                ? "3 bağımsız kontrol de tamamlandı; orchestrator devam edebilir."
                : "Latch " + awaitTimeoutMs + "ms içinde sıfıra inmedi (remainingCount=" + remaining + "). " +
                        "simulateCrashWithoutFinally=true ise bu BEKLENEN sonuçtur: FraudCheck finally olmadan " +
                        "exception fırladığı için countDown() hiç çalışmadı ve latch kalıcı olarak 1'de kaldı " +
                        "(production'da timeout yoksa bu orchestrator'ı SONSUZA KADAR bekletirdi).";
        return new CountDownLatchResponse(completedInTime, remaining, customerDone.get(), fraudDone.get(),
                pricingDone.get(), elapsedMs, note);
    }

    /** GOOD: countDown() finally içinde — worker başarılı da olsa, exception da fırlatsa her zaman çalışır. */
    private void safeWorker(String name, long delayMs, CountDownLatch latch, AtomicBoolean doneFlag) {
        try {
            Thread.sleep(delayMs);
            doneFlag.set(true);
            log.debug("{} completed", name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();
        }
    }

    /**
     * BAD (yalnızca simulateCrashWithoutFinally=true iken çalışır): countDown() bilinçli olarak finally
     * içinde DEĞİL. Worker exception fırlattığında countDown() hiç çağrılmaz; latch kalıcı olarak takılır.
     * Bu, "finally olmadan countDown" riskinin canlı gösterimidir — production kodu DEĞİLDİR.
     */
    private void unsafeWorker(String name, long delayMs, CountDownLatch latch) {
        try {
            Thread.sleep(delayMs);
            throw new RuntimeException("Simulated " + name + " crash BEFORE countDown (BAD: no finally)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // BİLİNÇLİ: burada latch.countDown() YOK. Gerçek projede bu, "exception handling eklendi
            // ama countDown unutuldu" şeklinde ortaya çıkan, teşhisi zor bir production bug'ıdır.
            log.error("{} crashed WITHOUT finally-protected countDown: {}", name, e.getMessage());
        }
    }

    private boolean awaitLatch(CountDownLatch latch, long timeoutMs) {
        try {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CountDownLatch lab interrupted while awaiting", e);
        }
    }
}
