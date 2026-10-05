package com.javalabs.concurrency.downstream;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import com.javalabs.concurrency.provider.FraudProviderSimulator;
import com.javalabs.concurrency.provider.ProviderOverloadedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 5 — BAD: Virtual Thread + kontrolsüz downstream çağrısı.
 *
 * <p>Bu lab'ın tek amacı şu yanılgıyı ÇÖKERTMEKTIR: "500 Virtual Thread oluşturabiliyorum, demek ki
 * provider'a 500 concurrent request gönderebilirim." Virtual Thread, JVM içindeki TASK EXECUTION
 * modelini ölçekler (çok task'ı az OS thread ile ifade eder) — provider'ın kapasitesiyle hiçbir ilgisi
 * yoktur. requestCount, FraudProviderSimulator'ın maxConcurrency'sini (varsayılan 10) aştığında provider
 * gerçekten ProviderOverloadedException fırlatır; bu exception burada YUTULMAZ, sayılır.
 */
@Service
public class UncontrolledDownstreamLab {

    private static final Logger log = LoggerFactory.getLogger(UncontrolledDownstreamLab.class);

    private final FraudProviderSimulator fraudProvider;
    private final ConcurrencyLabProperties.Safety safety;

    public UncontrolledDownstreamLab(FraudProviderSimulator fraudProvider, ConcurrencyLabProperties properties) {
        this.fraudProvider = fraudProvider;
        this.safety = properties.safety();
    }

    public DownstreamResponse run(int requestCount) {
        LabInputValidation.requireRange("requestCount", requestCount, 1, safety.maxDownstreamRequestCount());

        log.info("Uncontrolled downstream lab (BAD) starting: requestCount={} providerMaxConcurrency={}",
                requestCount, fraudProvider.maxConcurrency());

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger overloaded = new AtomicInteger();

        long startNanos = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(requestCount);
        // Her request kendi Virtual Thread'inde, ARADA HİÇBİR GATE/LİMİT OLMADAN provider'ı çağırır.
        // Bu BİLİNÇLİ olarak kötü bir implementasyondur (BAD): Virtual Thread oluşturmak ucuz olduğu için
        // "oluşturabiliyorum" ile "downstream'in kaldırabileceği" karıştırılmıştır.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requestCount; i++) {
                int orderId = i;
                futures.add(executor.submit(() -> callProviderUncontrolled(orderId, accepted, overloaded)));
            }
            for (Future<?> f : futures) {
                waitQuietly(f);
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        var stats = fraudProvider.stats();
        log.info("Uncontrolled downstream lab (BAD) finished: accepted={} overloaded={} maxObservedConcurrency={} " +
                        "providerMaxConcurrency={} limitRespected={}",
                accepted.get(), overloaded.get(), stats.observedMaxConcurrency(), stats.maxConcurrency(),
                stats.limitRespected());

        String note = stats.limitRespected()
                ? "Bu çalıştırmada requestCount, provider limitini aşacak kadar yüksek eşzamanlılık üretmedi. " +
                        "requestCount'u artırıp tekrar deneyin (örn. provider maxConcurrency'nin birkaç katı)."
                : "500 Virtual Thread oluşturabilmek, provider'ın 500 concurrent request kaldırabileceği anlamına " +
                        "GELMEZ: maxObservedConcurrency providerMaxConcurrency'yi aştı ve " + overloaded.get() +
                        " çağrı reddedildi. Karşılaştırın: /api/labs/virtual/downstream/good (Semaphore).";
        return new DownstreamResponse("VIRTUAL_THREAD_UNCONTROLLED", requestCount, fraudProvider.maxConcurrency(),
                accepted.get(), overloaded.get(), stats.observedMaxConcurrency(), stats.limitRespected(), elapsedMs,
                note);
    }

    private void callProviderUncontrolled(int orderId, AtomicInteger accepted, AtomicInteger overloaded) {
        try {
            fraudProvider.check("order-" + orderId);
            accepted.incrementAndGet();
        } catch (ProviderOverloadedException e) {
            overloaded.incrementAndGet();
        }
    }

    private void waitQuietly(Future<?> f) {
        try {
            f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Uncontrolled downstream lab interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Uncontrolled downstream task failed", e.getCause());
        }
    }
}
