package com.javalabs.concurrency.coordination;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 8 — CyclicBarrier.
 *
 * <p>Gerçek senaryo: bir batch'in 3 SABİT worker'ı, chunk'ın "phase 1" (validate) işini bitirir ama
 * HİÇBİRİ "phase 2"ye (process) diğerleri hazır olmadan geçmemelidir — peer-to-peer senkronizasyon.
 * CountDownLatch'ten (Lab 7) farkı: latch "N bağımsız olayın tamamlanmasını bekleyen 1 taraf" modelidir;
 * CyclicBarrier "birbirini bekleyen N eşit taraf" modelidir. Ayrıca CyclicBarrier REUSABLE'dır: tüm
 * parties bir kez barrier'a ulaştığında otomatik resetlenir ve aynı instance bir sonraki round için
 * tekrar kullanılabilir (rounds>1 parametresi bunu kanıtlar).
 */
@Service
public class CyclicBarrierLab {

    private static final Logger log = LoggerFactory.getLogger(CyclicBarrierLab.class);
    private static final int WORKER_COUNT = 3;

    private final ConcurrencyLabProperties.Safety safety;

    public CyclicBarrierLab(ConcurrencyLabProperties properties) {
        this.safety = properties.safety();
    }

    public CyclicBarrierResponse run(List<Long> workerDelaysMs, int rounds) {
        if (workerDelaysMs == null || workerDelaysMs.size() != WORKER_COUNT) {
            throw new IllegalArgumentException("workerDelaysMs must contain exactly " + WORKER_COUNT + " values");
        }
        for (Long delay : workerDelaysMs) {
            LabInputValidation.requireRange("workerDelaysMs", delay, 0, safety.maxDelayMs());
        }
        LabInputValidation.requireRange("rounds", rounds, 1, 3);

        AtomicInteger currentRound = new AtomicInteger();
        // Barrier action: SADECE son worker arrive ettiğinde, barrier'ı tetikleyen thread tarafından çalışır.
        // Log spam olmaz çünkü round başına bir kez tetiklenir.
        CyclicBarrier barrier = new CyclicBarrier(WORKER_COUNT, () ->
                log.info("BARRIER TRIPPED: all {} workers reached phase-1 end for round {} -> advancing to phase 2",
                        WORKER_COUNT, currentRound.get()));

        log.info("CyclicBarrier lab starting: workerDelaysMs={} rounds={}", workerDelaysMs, rounds);

        long startNanos = System.nanoTime();
        List<CyclicBarrierResponse.RoundResult> roundResults = new ArrayList<>();
        for (int round = 1; round <= rounds; round++) {
            currentRound.set(round);
            roundResults.add(runRound(round, workerDelaysMs, barrier, startNanos));
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        boolean allVerified = roundResults.stream().allMatch(CyclicBarrierResponse.RoundResult::verified);

        log.info("CyclicBarrier lab finished: rounds={} allVerified={} elapsedMs={}", rounds, allVerified, elapsedMs);

        String note = "verified=true demek: bu round'da HİÇBİR worker'ın phase2StartMs'i, O ROUNDDAKİ EN YAVAŞ " +
                "worker'ın phase1EndMs'inden önce değildir -> barrier gerçekten hiçbirini erken bırakmadı. " +
                "rounds>1 iken AYNI CyclicBarrier instance'ı tekrar kullanıldı (reusable/cyclic); " +
                "CountDownLatch bunu yapamaz (bkz. Lab 7).";
        return new CyclicBarrierResponse(rounds, roundResults, allVerified, elapsedMs, note);
    }

    private CyclicBarrierResponse.RoundResult runRound(int round, List<Long> workerDelaysMs, CyclicBarrier barrier,
                                                        long startNanos) {
        long[] phase1End = new long[WORKER_COUNT];
        long[] phase2Start = new long[WORKER_COUNT];
        List<Thread> threads = new ArrayList<>(WORKER_COUNT);
        for (int w = 0; w < WORKER_COUNT; w++) {
            int workerId = w;
            long delay = workerDelaysMs.get(w);
            threads.add(Thread.ofVirtual().name("barrier-r" + round + "-worker" + workerId).start(
                    () -> runWorker(workerId, delay, barrier, startNanos, phase1End, phase2Start)));
        }
        joinAll(threads);

        boolean verified = verifyOrdering(phase1End, phase2Start);
        return new CyclicBarrierResponse.RoundResult(round, toList(phase1End), toList(phase2Start), verified);
    }

    private void runWorker(int workerId, long delayMs, CyclicBarrier barrier, long startNanos, long[] phase1End,
                            long[] phase2Start) {
        try {
            Thread.sleep(delayMs); // Phase 1: batch chunk validation
            phase1End[workerId] = elapsedMs(startNanos);

            // Timeout KULLANILIYOR: bir worker hiç gelmezse (örn. hata/deadlock) diğerleri sonsuza kadar
            // beklemesin — BrokenBarrierException/TimeoutException ile gözlemlenebilir şekilde patlasın.
            barrier.await(30, TimeUnit.SECONDS);

            phase2Start[workerId] = elapsedMs(startNanos);
            Thread.sleep(10); // Phase 2: sembolik "process chunk" işi
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException | TimeoutException e) {
            log.error("Worker {} barrier wait failed: {}", workerId, e.getMessage());
        }
    }

    private boolean verifyOrdering(long[] phase1End, long[] phase2Start) {
        long maxPhase1End = Arrays.stream(phase1End).max().orElse(0);
        long minPhase2Start = Arrays.stream(phase2Start).min().orElse(0);
        return maxPhase1End <= minPhase2Start;
    }

    private void joinAll(List<Thread> threads) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("CyclicBarrier lab interrupted while waiting for workers", e);
            }
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private List<Long> toList(long[] arr) {
        List<Long> list = new ArrayList<>(arr.length);
        for (long v : arr) {
            list.add(v);
        }
        return list;
    }
}
