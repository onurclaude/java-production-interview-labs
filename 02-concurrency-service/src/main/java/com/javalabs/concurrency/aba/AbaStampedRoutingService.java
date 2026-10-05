package com.javalabs.concurrency.aba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Lab 12 — GOOD: AtomicStampedReference ile ABA'nın yakalanması.
 *
 * <p>Stamp, her değişiklikte artan bir "versiyon numarası"dır. compareAndSet/get işlemleri HEM referansı
 * HEM stamp'i birlikte kontrol eder. A -> B -> A geçişinde son değer ilk değere referans olarak eşit
 * olsa da, stamp İKİ KEZ artmıştır (A->B, B->A); bu yüzden "value aynı, ama stamp farklı" tespiti ile
 * aradaki gerçek değişiklik YAKALANIR. Stamp'in gerekliliği tam olarak budur: value-equality'nin
 * kaçırdığı "arada bir şey oldu" bilgisini taşımak.
 */
@Service
public class AbaStampedRoutingService {

    private static final Logger log = LoggerFactory.getLogger(AbaStampedRoutingService.class);

    private final AtomicStampedReference<ProviderRoutingState> ref =
            new AtomicStampedReference<>(ProviderRoutingState.PROVIDER_A, 0);
    private final AtomicLong totalRuns = new AtomicLong();
    private final AtomicLong bugPreventedCount = new AtomicLong();

    public AbaStampedResult demonstrate() {
        totalRuns.incrementAndGet();
        ref.set(ProviderRoutingState.PROVIDER_A, 0); // deterministik başlangıç: her çalıştırma stamp=0'dan başlar

        int[] stampHolder = new int[1];
        ProviderRoutingState before = ref.get(stampHolder);
        int stampBefore = stampHolder[0];

        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch flipsDone = new CountDownLatch(1);
        Thread flipper = Thread.ofVirtual().name("aba-stamped-flipper").start(() -> {
            await(snapshotTaken);
            ref.set(ProviderRoutingState.PROVIDER_B, stampBefore + 1);
            ref.set(ProviderRoutingState.PROVIDER_A, stampBefore + 2);
            flipsDone.countDown();
        });

        snapshotTaken.countDown();
        await(flipsDone);
        join(flipper);

        ProviderRoutingState after = ref.get(stampHolder);
        int stampAfter = stampHolder[0];

        boolean referenceUnchanged = (before == after);
        // GOOD MANTIK: value-equality yetmez, stamp da aynı olmalı. A->B->A iki stamp artışı yaptığı için
        // stampBefore != stampAfter olur -> reallyUnchanged=false, aradaki değişiklik YAKALANIR.
        boolean reallyUnchanged = referenceUnchanged && (stampBefore == stampAfter);
        boolean bugPrevented = referenceUnchanged && !reallyUnchanged;
        if (bugPrevented) {
            bugPreventedCount.incrementAndGet();
        }

        log.info("ABA GOOD demo: before={} after={} stampBefore={} stampAfter={} reallyUnchanged={}",
                before.providerName(), after.providerName(), stampBefore, stampAfter, reallyUnchanged);

        String note = reallyUnchanged
                ? "stampBefore==stampAfter: gerçekten hiçbir değişiklik olmadı."
                : "before==after (referans) olsa da stampBefore(" + stampBefore + ") != stampAfter(" + stampAfter +
                        "): arada 2 kez state değişti (A->B->A) ve bu, plain AtomicReference'ın (Lab 12 BAD) " +
                        "KAÇIRDIĞI bir bilgidir. AtomicStampedReference ABA'yı tam olarak bu şekilde yakalar.";
        return new AbaStampedResult(before.providerName(), after.providerName(), stampBefore, stampAfter,
                referenceUnchanged, reallyUnchanged, bugPrevented, totalRuns.get(), bugPreventedCount.get(), note);
    }

    public AbaStampedStats stats() {
        return new AbaStampedStats(totalRuns.get(), bugPreventedCount.get());
    }

    public void reset() {
        ref.set(ProviderRoutingState.PROVIDER_A, 0);
        totalRuns.set(0);
        bugPreventedCount.set(0);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("ABA GOOD demo timed out waiting for coordination latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ABA GOOD demo interrupted", e);
        }
    }

    private void join(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ABA GOOD demo interrupted while joining flipper thread", e);
        }
    }
}
