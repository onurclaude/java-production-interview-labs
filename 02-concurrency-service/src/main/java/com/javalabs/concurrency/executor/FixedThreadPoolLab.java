package com.javalabs.concurrency.executor;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.common.ThreadModel;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import com.javalabs.concurrency.observability.ThreadSnapshot;
import com.javalabs.concurrency.provider.CustomerCheckSimulator;
import com.javalabs.concurrency.thread.ThreadWorkloadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 2 — FixedThreadPool.
 *
 * <p>GÜNLÜK HAYAT BENZETMESİ: 100 işimiz var ama sadece 10 çalışanımız (worker = thread) var. Aynı anda
 * en fazla 10 iş işlenebilir; 11. iş, bir çalışan boşalana kadar SIRADA (queue) bekler. {@code poolSize}
 * = çalışan sayısı.
 *
 * <p>AYNI {@link CustomerCheckSimulator} kullanılır (Platform Thread lab'ıyla — Lab 1 — karşılaştırılabilir
 * olsun diye, aynı iş yükü). Fark: task'lar artık KENDİ thread'lerini açmaz (Lab 1'deki gibi), sabit sayıda
 * worker'dan oluşan bir havuza GİRER. {@code newFixedThreadPool(poolSize)} ham thread açmaktan daha
 * kontrollüdür çünkü aynı anda çalışan OS thread sayısını {@code poolSize} ile SINIRLAR (Lab 1'de bu
 * sınır yoktu, taskCount kadar thread açılıyordu).
 *
 * <p>DİKKAT — production riski: 100 iş değil 1.000.000 iş birden gelirse ne olur? {@code poolSize}
 * dolduğunda fazla işler worker'ları BEKLEMEZ diye reddedilmez — Executors.newFixedThreadPool()'un içindeki
 * {@code LinkedBlockingQueue} SINIRSIZDIR, yani kuyruk sessizce büyümeye devam eder. 1 milyon iş = kuyrukta
 * 1 milyon bekleyen task = büyüyen heap, artan GC baskısı, sonunda OutOfMemoryError. "FixedThreadPool
 * kullandım, kontrollüyüm" yanılgısı tam olarak burada kırılır — kontrollü olan sadece worker SAYISIDIR,
 * kuyruk BÜYÜKLÜĞÜ değil (bkz. Lab 3, kuyruğu da sınırlayan {@code ThreadPoolExecutor}).
 */
@Service
public class FixedThreadPoolLab {

    private static final Logger log = LoggerFactory.getLogger(FixedThreadPoolLab.class);

    private final CustomerCheckSimulator customerCheck;
    private final ConcurrencyLabProperties.Safety safety;

    public FixedThreadPoolLab(CustomerCheckSimulator customerCheck, ConcurrencyLabProperties properties) {
        this.customerCheck = customerCheck;
        this.safety = properties.safety();
    }

    public ThreadWorkloadResponse run(int taskCount, int poolSize, long delayMs) {
        LabInputValidation.requireRange("taskCount", taskCount, 1, safety.maxFixedPoolTaskCount());
        LabInputValidation.requireRange("poolSize", poolSize, 1, safety.maxFixedPoolSize());
        LabInputValidation.requireRange("delayMs", delayMs, 0, safety.maxDelayMs());

        log.info("FixedThreadPool lab starting: taskCount={} poolSize={} delayMs={}", taskCount, poolSize, delayMs);

        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        ThreadFactory factory = Thread.ofPlatform().name("fixed-pool-worker-", 0).factory();

        long startNanos = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(taskCount);
        // newFixedThreadPool(poolSize, factory): poolSize sabit worker, queue SINIRSIZ (LinkedBlockingQueue).
        ExecutorService executor = Executors.newFixedThreadPool(poolSize, factory);
        try {
            for (int i = 0; i < taskCount; i++) {
                int taskId = i;
                futures.add(executor.submit(
                        () -> runTask(taskId, delayMs, activeCount, maxObserved, completed)));
            }
            awaitAll(futures);
        } finally {
            shutdownAndAwait(executor);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("FixedThreadPool lab finished: completed={}/{} elapsedMs={} maxObservedConcurrency={} poolSize={}",
                completed.get(), taskCount, elapsedMs, maxObserved.get(), poolSize);

        String note = "maxObservedConcurrency poolSize'ı AŞAMAZ (gözlemleyin: ~= min(taskCount, poolSize)). " +
                "Ama bu 'production-safe' anlamına gelmez: fazla task'lar reddedilmez, newFixedThreadPool'un " +
                "SINIRSIZ queue'sunda birikir. Sürekli taskCount > poolSize olursa queue büyür büyür büyür " +
                "(unbounded memory growth) — bkz. /api/labs/executor/bounded.";
        return new ThreadWorkloadResponse(ThreadModel.FIXED_THREAD_POOL, taskCount, completed.get(), elapsedMs,
                false, maxObserved.get(), note);
    }

    private void runTask(int taskId, long delayMs, AtomicInteger activeCount, AtomicInteger maxObserved,
                          AtomicInteger completed) {
        int now = activeCount.incrementAndGet();
        maxObserved.getAndAccumulate(now, Math::max);
        try {
            if (log.isDebugEnabled()) {
                ThreadSnapshot snap = ThreadSnapshot.current();
                log.debug("task={} thread={} id={} activeNow={}", taskId, snap.name(), snap.id(), now);
            }
            customerCheck.check("order-" + taskId, delayMs);
            completed.incrementAndGet();
        } finally {
            activeCount.decrementAndGet();
        }
    }

    private void awaitAll(List<Future<?>> futures) {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("FixedThreadPool lab interrupted while waiting for tasks", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("FixedThreadPool task failed", e.getCause());
            }
        }
    }

    /**
     * ExecutorService'i shutdown etmemek kaynak sızıntısıdır: pool thread'leri non-daemon'dur ve
     * shutdown edilmezse hem kaynak tutmaya devam eder hem de (bu lab'da olmasa da) JVM'in kapanmasını
     * engelleyebilir. Her request kendi executor'ını açıp bu method'da deterministik şekilde kapatır.
     */
    private void shutdownAndAwait(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
