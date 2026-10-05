package com.javalabs.concurrency.downstream;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import com.javalabs.concurrency.provider.FraudProviderSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 6 — GOOD: Virtual Thread + Semaphore (bulkhead).
 *
 * <p>PROBLEM: {@link UncontrolledDownstreamLab} (Lab 5) otobüsteki 500 kişiyi doğrudan 10 kişilik kapıya
 * gönderip overload'a sebep oluyordu. Virtual Thread'den vazgeçmeden bunu nasıl önleriz?
 *
 * <p>ÇÖZÜM: Kapının önüne, provider'ın kapasitesi kadar ({@code permits}) GİRİŞ KARTI olan bir kart
 * dağıtıcısı koyuyoruz — bu, {@link Semaphore}'un ta kendisidir:
 * <ul>
 *   <li>İçeri girmek isteyen her istek önce {@code tryAcquire()}/{@code acquire()} ile bir KART ister.</li>
 *   <li>10 kart varsa, 10. isteğe kadar herkes kart alır ve içeri girer (provider'ı çağırır).</li>
 *   <li>11. istek geldiğinde kart KALMAMIŞTIR: ya kart boşalana kadar BEKLER ({@code acquire()}),
 *       ya da belirli bir süre bekleyip vazgeçer ({@code tryAcquire(timeout)}).</li>
 *   <li>İş bitince kart GERİ VERİLİR ({@code release()}) — böylece sıradaki istek kartı alabilir.</li>
 * </ul>
 * Virtual Thread ile Semaphore birbirinin ALTERNATİFİ DEĞİLDİR, iki farklı katmanı çözerler:
 * Virtual Thread TASK EXECUTION modelini ölçekler (500 kişiyi otobüse bindirebiliriz); Semaphore
 * DOWNSTREAM KAPASİTESİNİ korur (kapıdan aynı anda sadece 10 kişi geçer). Burada ikisi BİRLİKTE
 * kullanılır.
 *
 * <p>DİKKAT (JVM-local sınır): Bu kart dağıtıcısı sadece BU JVM'in belleğinde yaşar. Uygulama 3 pod
 * olarak çalışıyorsa, her pod'un kendi 10 kartlık dağıtıcısı olur ve provider'a toplamda 30 concurrent
 * request gidebilir — tıpkı 01-atomic-service'teki AtomicInteger'ın multi-pod sınırı gibi. Global limit
 * için paylaşılan bir koordinasyon noktası (örn. Redis tabanlı bir rate limiter) gerekir; BU LAB BUNU
 * IMPLEMENT ETMEZ, sadece sınırı doğru anlamanız içindir (bkz. README "JVM-local vs Distributed Concurrency").
 */
@Service
public class SemaphoreBulkheadLab {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreBulkheadLab.class);

    private final FraudProviderSimulator fraudProvider;
    private final ConcurrencyLabProperties.Safety safety;
    // Permit sayısı provider'ın gerçek kapasitesiyle AYNI config'ten gelir: iki ayrı sayı olsaydı
    // (biri burada, biri simulator'da) birbirinden sürüklenip senkronize kalamayabilirdi.
    private final Semaphore providerGate;

    public SemaphoreBulkheadLab(FraudProviderSimulator fraudProvider, ConcurrencyLabProperties properties) {
        this.fraudProvider = fraudProvider;
        this.safety = properties.safety();
        this.providerGate = new Semaphore(fraudProvider.maxConcurrency());
    }

    public DownstreamResponse run(int requestCount, boolean useTimeout, long timeoutMs) {
        LabInputValidation.requireRange("requestCount", requestCount, 1, safety.maxDownstreamRequestCount());
        if (useTimeout) {
            LabInputValidation.requireRange("timeoutMs", timeoutMs, 0, safety.maxDelayMs());
        }

        log.info("Semaphore bulkhead lab (GOOD) starting: requestCount={} permits={} useTimeout={} timeoutMs={}",
                requestCount, providerGate.availablePermits(), useTimeout, timeoutMs);

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejectedOrTimedOut = new AtomicInteger();

        long startNanos = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(requestCount);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requestCount; i++) {
                int orderId = i;
                futures.add(executor.submit(
                        () -> callProviderThroughGate(orderId, useTimeout, timeoutMs, accepted, rejectedOrTimedOut)));
            }
            for (Future<?> f : futures) {
                waitQuietly(f);
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        var stats = fraudProvider.stats();
        log.info("Semaphore bulkhead lab (GOOD) finished: accepted={} rejectedOrTimedOut={} " +
                        "maxObservedConcurrency={} providerMaxConcurrency={} limitRespected={}",
                accepted.get(), rejectedOrTimedOut.get(), stats.observedMaxConcurrency(), stats.maxConcurrency(),
                stats.limitRespected());

        String note = "Semaphore permit sayısı provider kapasitesiyle eşit (" + fraudProvider.maxConcurrency() +
                "); bu yüzden maxObservedConcurrency <= providerMaxConcurrency OLMALI (BAD lab'daki ihlal burada " +
                "gözlenmemelidir). Bu garanti sadece BU JVM içindir — bkz. README multi-pod sınırı.";
        return new DownstreamResponse(useTimeout ? "VIRTUAL_THREAD_SEMAPHORE_TIMEOUT" : "VIRTUAL_THREAD_SEMAPHORE",
                requestCount, fraudProvider.maxConcurrency(), accepted.get(), rejectedOrTimedOut.get(),
                stats.observedMaxConcurrency(), stats.limitRespected(), elapsedMs, note);
    }

    /**
     * Tek bir isteğin kart alma -> kapıdan geçme -> kartı geri verme döngüsü.
     *
     * <p>{@code release()} NEDEN finally İÇİNDE? Şunu düşünün: 100 istek, 10 kart. Her istek kartı aldıktan
     * sonra provider'ı çağırıyor. Eğer provider o çağrıda exception fırlatırsa (örn. {@code fail=true} ya
     * da burada: provider'ın kendi kapasite kontrolü) ve {@code release()} finally DIŞINDA bir yerde olsaydı,
     * o kart ASLA GERİ VERİLMEZDİ. 10 çağrıdan 3'ü böyle başarısız olsa, elimizde fiilen 7 kart kalırdı —
     * Semaphore'un kendi sayacı hâlâ "10 kapasitem var" dese de gerçek kapasite sessizce erirdi. Bu, tıpkı
     * 01-atomic-service'teki "counter leak" bug'ının Semaphore karşılığıdır: kaynağı almak her zaman
     * kaynağı geri vermeyi GARANTİ ETMEZ, bunu dilin {@code finally} mekanizmasıyla zorlamak gerekir.
     */
    private void callProviderThroughGate(int orderId, boolean useTimeout, long timeoutMs, AtomicInteger accepted,
                                          AtomicInteger rejectedOrTimedOut) {
        boolean permitAcquired = acquirePermit(useTimeout, timeoutMs);
        if (!permitAcquired) {
            rejectedOrTimedOut.incrementAndGet();
            return;
        }
        try {
            fraudProvider.check("order-" + orderId);
            accepted.incrementAndGet();
        } finally {
            providerGate.release();
        }
    }

    private boolean acquirePermit(boolean useTimeout, long timeoutMs) {
        try {
            if (useTimeout) {
                // tryAcquire(timeout): production endpoint'te sonsuza kadar beklemek istemeyiz — request
                // thread'i (burada: virtual thread) downstream kapasitesi dolu diye süresiz asılı kalmamalı.
                return providerGate.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            }
            // acquire(): sınırsız bekler. Düşük-orta yük için makul olabilir ama request'in ne kadar
            // bekleyeceğine bir üst sınır koymaz; yük arttığında bekleyen thread sayısı sessizce büyür.
            providerGate.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void waitQuietly(Future<?> f) {
        try {
            f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Semaphore bulkhead lab interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Semaphore bulkhead task failed", e.getCause());
        }
    }

    public int availablePermits() {
        return providerGate.availablePermits();
    }
}
