package com.javalabs.concurrency.aba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lab 12 — BAD: plain AtomicReference ile ABA problemi.
 *
 * <p>PROBLEM — gerçek bir production bug'ı şöyle doğar: bir servis "routing state'i ben bakmadan önce
 * değişti mi?" sorusunu SADECE before/after REFERANSINI karşılaştırarak cevaplar (örn. "değişmediyse,
 * elimdeki cache'lenmiş bağlantıyı/kararı yeniden kullanmak güvenlidir" gibi bir optimizasyon).
 *
 * <p>Somut zaman çizelgesi (Thread-A karar verici, Thread-B arka planda routing'i değiştiren thread):
 * <pre>
 *   Thread-A: state.get() -> Provider-A        ("şu an A'dayız" diye not aldı)
 *   Thread-B: state.set(Provider-B)             (trafik B'ye kaydı)
 *   Thread-B: state.set(Provider-A)              (trafik GERİ A'ya döndü)
 *   Thread-A: state.get() -> Provider-A        ("hâlâ A! hiçbir şey değişmemiş" diye düşünüyor)
 * </pre>
 * Thread-A'nın gördüğü before/after AYNI REFERANSTIR (her ikisi de aynı paylaşılan {@code PROVIDER_A}
 * nesnesi) — ama arada GERÇEKTEN B'ye gidip geldik. Thread-A'nın "hiçbir şey değişmedi" varsayımına
 * dayanan her kararı (bir sayaç, bir cache, bir circuit-breaker durumu) sessizce YANLIŞ olur, çünkü o
 * pencerede trafiğin bir kısmı gerçekten B'ye gitmiş olabilir.
 *
 * <p>DİKKAT: Bu lab deterministiktir — A->B->A sırası şansa bırakılmaz, {@code CountDownLatch} ile
 * Thread-A'nın "before" okumasını bitirmesi ile Thread-B'nin flip'lere başlaması arasına kesin bir sıra
 * konur (aksi halde flip'ler bazen "before" okumasından ÖNCE bitebilir ve lab her çalıştırmada farklı
 * sonuç verirdi).
 */
@Service
public class AbaBadRoutingService {

    private static final Logger log = LoggerFactory.getLogger(AbaBadRoutingService.class);

    private final AtomicReference<ProviderRoutingState> state =
            new AtomicReference<>(ProviderRoutingState.PROVIDER_A);
    private final AtomicLong totalRuns = new AtomicLong();
    private final AtomicLong bugTriggeredCount = new AtomicLong();

    public AbaBadResult demonstrate() {
        totalRuns.incrementAndGet();
        state.set(ProviderRoutingState.PROVIDER_A); // her çalıştırma deterministik aynı başlangıçtan başlar

        ProviderRoutingState before = state.get();

        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch flipsDone = new CountDownLatch(1);
        Thread flipper = Thread.ofVirtual().name("aba-bad-flipper").start(() -> {
            await(snapshotTaken);
            // A -> B -> A: before==after (reference equality) olacak, ama arada GERÇEKTEN B'ye geçildi.
            state.set(ProviderRoutingState.PROVIDER_B);
            state.set(ProviderRoutingState.PROVIDER_A);
            flipsDone.countDown();
        });

        // snapshot alındıktan SONRA flip'lerin başladığını garanti ediyoruz: "before" gerçekten flip'lerden
        // önceki değerdir, timing şansa bağlı değildir.
        snapshotTaken.countDown();
        await(flipsDone);
        join(flipper);

        ProviderRoutingState after = state.get();

        // BAD MANTIK: "referans aynıysa state hiç değişmedi" varsayımı. Bu VARSAYIM burada YANLIŞTIR.
        boolean referenceUnchanged = (before == after);
        boolean bugDemonstrated = referenceUnchanged; // A->B->A flip'i hep gerçekleştiği için bu hep true olmalı
        if (bugDemonstrated) {
            bugTriggeredCount.incrementAndGet();
        }

        log.info("ABA BAD demo: before={} after={} referenceUnchanged={} (ama arada Provider-B'ye geçildi!)",
                before.providerName(), after.providerName(), referenceUnchanged);

        String note = "before==after (referans) olduğu için BAD kod 'state değişmedi' sonucuna varır. " +
                "Gerçekte A->B->A geçişi oldu; plain AtomicReference bu ara değişikliği YAKALAYAMAZ. " +
                "Karşılaştırın: POST /api/labs/aba/stamped (AtomicStampedReference stamp ile bunu yakalar).";
        return new AbaBadResult(before.providerName(), after.providerName(), referenceUnchanged, bugDemonstrated,
                totalRuns.get(), bugTriggeredCount.get(), note);
    }

    public AbaBadStats stats() {
        return new AbaBadStats(totalRuns.get(), bugTriggeredCount.get());
    }

    public void reset() {
        state.set(ProviderRoutingState.PROVIDER_A);
        totalRuns.set(0);
        bugTriggeredCount.set(0);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("ABA BAD demo timed out waiting for coordination latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ABA BAD demo interrupted", e);
        }
    }

    private void join(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ABA BAD demo interrupted while joining flipper thread", e);
        }
    }
}
