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
 * <p>Virtual Thread ile Semaphore birbirinin ALTERNATİFİ DEĞİLDİR:
 * <ul>
 *   <li>Virtual Thread: task execution modelini ölçekler (çok task'ı az OS thread ile çalıştırır).</li>
 *   <li>Semaphore: downstream (fraud provider) concurrency KAPASİTESİNİ korur — kaynağı kim çağırdığından
 *       bağımsız olarak "aynı anda en fazla N çağrı" garantisi verir.</li>
 * </ul>
 * Burada ikisi BİRLİKTE kullanılır: Virtual Thread çok sayıda isteği ucuza ifade eder, Semaphore bu
 * isteklerin provider'a aynı anda en fazla permits() kadarının ulaşmasını garanti eder.
 *
 * <p>Semaphore tek bir JVM'in bellek alanında yaşar: 3 pod varsa 3 ayrı Semaphore(10) vardır ve
 * provider'a toplamda 30 concurrent request gidebilir. Global limit için distributed bir koordinasyon
 * noktası (örn. Redis tabanlı bir rate limiter) gerekir — BU LAB'DA IMPLEMENT EDİLMEMİŞTİR, sadece sınırı
 * doğru anlamak önemlidir (bkz. README "JVM-local vs Distributed Concurrency").
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

    private void callProviderThroughGate(int orderId, boolean useTimeout, long timeoutMs, AtomicInteger accepted,
                                          AtomicInteger rejectedOrTimedOut) {
        boolean permitAcquired = acquirePermit(useTimeout, timeoutMs);
        if (!permitAcquired) {
            rejectedOrTimedOut.incrementAndGet();
            return;
        }
        // Permit release KESİNLİKLE finally içinde: provider.check() exception fırlatsa (örn. simulated
        // failure) bile permit geri verilmezse, bu permit kalıcı olarak kaybolur ve Semaphore zamanla
        // "sahte doluluğa" kilitlenir (gerçek kapasite varken kabul etmeyi durdurur) — en klasik Semaphore bug'ı.
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
