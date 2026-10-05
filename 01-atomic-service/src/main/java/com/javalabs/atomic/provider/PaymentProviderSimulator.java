package com.javalabs.atomic.provider;

import com.javalabs.atomic.config.AtomicLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dışarıdaki payment provider'ın kontrollü simülasyonu.
 *
 * <p>Önemli ayrım: Bu sınıftaki sayaçlar ÖLÇÜM içindir, limit uygulamak için değil.
 * Gerçek provider da "şu an kaç request işliyorum" bilgisini kendi tarafında tutar;
 * bizim uygulamamız bu bilgiye erişemez ve limiti kendi tarafında uygulamak zorundadır.
 * Bu yüzden simulator limit aşıldığında request'i reddetmez; sadece ihlali kayıt altına alır.
 * (Gerçek bir provider bu durumda 429/503 dönebilir, yavaşlayabilir veya hesabı throttle edebilir.)
 */
@Component
public class PaymentProviderSimulator {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderSimulator.class);

    private final int capacity;
    private final long minDelayMs;
    private final long maxDelayMs;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger observedMaxConcurrency = new AtomicInteger();
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong capacityViolationCalls = new AtomicLong();

    public PaymentProviderSimulator(AtomicLabProperties properties) {
        this.capacity = properties.providerConcurrencyLimit();
        this.minDelayMs = properties.provider().minDelay().toMillis();
        this.maxDelayMs = properties.provider().maxDelay().toMillis();
    }

    public ProviderChargeResult charge(String paymentId, boolean fail) {
        int nowInFlight = inFlight.incrementAndGet();
        totalCalls.incrementAndGet();
        try {
            recordObservedConcurrency(nowInFlight);

            long delayMs = simulatedDelayMs();
            // Provider'ın gerçek işlem süresi. Bu sürede request "aktif" sayılır; overlap buradan doğar.
            Thread.sleep(Duration.ofMillis(delayMs));

            if (fail) {
                // Deterministik failure: sadece çağıran fail=true istediğinde. Random failure yok; lab tekrar üretilebilir olmalı.
                throw new ProviderFailureException("Provider rejected payment " + paymentId + " (simulated failure)");
            }
            return new ProviderChargeResult(paymentId, nowInFlight, delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderFailureException("Provider call interrupted for " + paymentId, e);
        } finally {
            // Simulator kendi ölçümünü finally ile güvenceye alıyor: exception olsa da "aktif" sayısı düşer.
            inFlight.decrementAndGet();
        }
    }

    /**
     * Maksimum değeri güncellemek de bir check-then-act'tir ("yeni değer büyükse yaz").
     * accumulateAndGet bunu içeride CAS döngüsüyle atomik yapar; get() + set() yazsaydık
     * iki thread birbirinin maksimumunu ezebilirdi ve ölçümün kendisi yanlış olurdu.
     */
    private void recordObservedConcurrency(int nowInFlight) {
        int previousMax = observedMaxConcurrency.getAndAccumulate(nowInFlight, Math::max);

        if (nowInFlight > capacity) {
            capacityViolationCalls.incrementAndGet();
            // Log spam olmasın diye sadece yeni bir rekor kırıldığında yazıyoruz.
            if (nowInFlight > previousMax) {
                log.warn("PROVIDER CAPACITY EXCEEDED: inFlight={} > capacity={} (new observed max)", nowInFlight, capacity);
            }
        }
    }

    private long simulatedDelayMs() {
        if (minDelayMs == maxDelayMs) {
            return minDelayMs;
        }
        // Süre rastgele olabilir (gerçek network/provider latency'si gibi); failure ise asla rastgele değil.
        return ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
    }

    public ProviderStats stats() {
        int max = observedMaxConcurrency.get();
        return new ProviderStats("LOCAL_JVM", capacity, inFlight.get(), max,
                totalCalls.get(), capacityViolationCalls.get(), max <= capacity);
    }

    /**
     * inFlight bilinçli olarak sıfırlanmaz: try/finally ile kendini dengeleyen gerçek bir sayaçtır.
     * Uçuştaki çağrılar varken sıfırlansaydı, onların finally'si sayacı negatife düşürürdü.
     * Maksimum, o anki gerçek inFlight değerine çekilir.
     */
    public void resetObservations() {
        observedMaxConcurrency.set(inFlight.get());
        totalCalls.set(0);
        capacityViolationCalls.set(0);
    }
}
