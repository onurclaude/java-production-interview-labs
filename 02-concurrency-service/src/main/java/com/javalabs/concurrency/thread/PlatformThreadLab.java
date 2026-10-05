package com.javalabs.concurrency.thread;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.common.ThreadModel;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import com.javalabs.concurrency.observability.ThreadSnapshot;
import com.javalabs.concurrency.provider.CustomerCheckSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 1 — Platform Thread.
 *
 * <p>Her task için çıplak {@code new Thread(...)} oluşturulur; havuzlama veya üst sınır YOKTUR
 * (güvenlik sınırı dışında). Amaç "Platform Thread kötüdür" göstermek değil, OS thread'inin gerçek
 * maliyetini (her thread kendi stack'ini ayırır, OS scheduler'a kaydolur) çıplak haliyle gözlemlemektir.
 * Az sayıda, kısa süreli, nadiren çalışan blocking task için bu YAKLAŞIM GAYET DOĞALDIR — her şeyi
 * executor/pool arkasına almak gereksiz bir abstraction'dır. Sorun, bu modelin taskCount büyüdükçe
 * (binlerce concurrent blocking I/O) doğrusal olmayan şekilde pahalılaşmasıdır.
 */
@Service
public class PlatformThreadLab {

    private static final Logger log = LoggerFactory.getLogger(PlatformThreadLab.class);

    private final CustomerCheckSimulator customerCheck;
    private final ConcurrencyLabProperties.Safety safety;

    public PlatformThreadLab(CustomerCheckSimulator customerCheck, ConcurrencyLabProperties properties) {
        this.customerCheck = customerCheck;
        this.safety = properties.safety();
    }

    public ThreadWorkloadResponse run(int taskCount, long delayMs) {
        LabInputValidation.requireRange("taskCount", taskCount, 1, safety.maxTaskCount());
        LabInputValidation.requireRange("delayMs", delayMs, 0, safety.maxDelayMs());

        log.info("Platform Thread lab starting: taskCount={} delayMs={}", taskCount, delayMs);

        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<Thread> threads = new ArrayList<>(taskCount);

        long startNanos = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            int taskId = i;
            Thread t = new Thread(() -> runTask(taskId, delayMs, activeCount, maxObserved, completed),
                    "platform-task-" + taskId);
            threads.add(t);
            t.start();
        }
        awaitAll(threads);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("Platform Thread lab finished: completed={}/{} elapsedMs={} maxObservedConcurrency={}",
                completed.get(), taskCount, elapsedMs, maxObserved.get());

        return new ThreadWorkloadResponse(ThreadModel.PLATFORM_THREAD, taskCount, completed.get(), elapsedMs, false,
                maxObserved.get(),
                "Her task kendi OS thread'inde çalıştı (havuzlama yok). maxObservedConcurrency ~= taskCount olmalı; " +
                        "bu, sınırsız platform thread oluşturmanın riskinin tam olarak kendisidir.");
    }

    private void runTask(int taskId, long delayMs, AtomicInteger activeCount, AtomicInteger maxObserved,
                          AtomicInteger completed) {
        int now = activeCount.incrementAndGet();
        maxObserved.getAndAccumulate(now, Math::max);
        try {
            if (log.isDebugEnabled()) {
                ThreadSnapshot snap = ThreadSnapshot.current();
                log.debug("task={} thread={} id={} virtual={} activeNow={}", taskId, snap.name(), snap.id(),
                        snap.virtual(), now);
            }
            customerCheck.check("order-" + taskId, delayMs);
            completed.incrementAndGet();
        } finally {
            activeCount.decrementAndGet();
        }
    }

    private void awaitAll(List<Thread> threads) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // Interrupt sinyalini yutmuyoruz: restore edip kalan join'leri denemeden çıkıyoruz.
                // Production'da bu durum "request thread'i cancel edildi" anlamına gelir.
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Platform thread lab interrupted while waiting for tasks", e);
            }
        }
    }
}
