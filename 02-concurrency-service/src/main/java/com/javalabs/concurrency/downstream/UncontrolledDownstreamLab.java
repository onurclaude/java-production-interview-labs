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
 * <p>PROBLEM: Uygulamamızın 500 kişilik bir otobüsü olduğunu düşünün (500 Virtual Thread rahatlıkla
 * oluşturulabilir). Ama gittiğimiz yerin (fraud provider) kapısından aynı anda sadece 10 kişi girebiliyor
 * (bkz. {@code concurrency-lab.provider.fraud.max-concurrency}). Otobüsün 500 kişi taşıyabilmesi,
 * kapının da 500 kişiyi aynı anda kabul edebileceği anlamına GELMEZ.
 *
 * <p>BU SINIFTA O KAPI YOK: her istek kendi Virtual Thread'inde, ARADA HİÇBİR SIRA/KONTROL OLMADAN
 * doğrudan provider'ın kapısına dayanıyor. 500 istek varsa 500'ü de AYNI ANDA kapıya varmaya çalışır.
 *
 * <p>SONUÇ: {@link FraudProviderSimulator}, aynı anda içeride (inFlight) olan istek sayısı 10'u
 * geçtiği an {@link ProviderOverloadedException} fırlatır — bu gerçek bir hatadır, burada yutulmaz,
 * sayılır ({@code overloaded} sayacı). "500 Virtual Thread açabiliyorum" ile "provider 500'ü kaldırır"
 * karıştırılınca production'da ortaya çıkan tam olarak budur: provider'dan art arda 429/503/overload
 * hataları gelmeye başlar, hem de uygulama tarafında HİÇBİR hata/exception görünmüyormuş gibi dursa bile
 * (çünkü Virtual Thread'leri açmak başarıyla çalışır — başarısız olan, onların HEPSİNİN aynı anda
 * provider'a ulaşmasıdır). Karşılaştırma için: {@link com.javalabs.concurrency.downstream.SemaphoreBulkheadLab}
 * aynı senaryoyu kapının önüne bir SIRA (Semaphore) koyarak çözer.
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
        /*
         * requestCount=100, providerMaxConcurrency=10 ile ne olur?
         *
         * Burada 100 Virtual Thread AYNI ANDA başlatılıyor ve hiçbiri diğerini beklemiyor. Provider'ın
         * iç sayacı (FraudProviderSimulator.inFlight) kabaca şöyle ilerler:
         *
         *   Thread-1  -> inFlight=1    (10'un altında, kabul)
         *   Thread-2  -> inFlight=2    (kabul)
         *   ...
         *   Thread-10 -> inFlight=10   (son izin verilen)
         *   Thread-11 -> inFlight=11   (LİMİT AŞILDI -> ProviderOverloadedException)
         *   Thread-12 -> inFlight=12   (AŞILDI -> exception)
         *   ...
         *
         * Gerçek test sonucu (requestCount=100): accepted=10, overloaded=90,
         * maxObservedConcurrency=100. Virtual Thread'lerin "ucuz" olması, hepsinin aynı anda
         * kapıya dayanmasını ENGELLEMEZ — tam tersine, kolaylaştırır.
         */
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
