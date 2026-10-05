package com.javalabs.concurrency.executor;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import com.javalabs.concurrency.provider.CustomerCheckSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 3 — Bounded ThreadPoolExecutor + Rejection.
 *
 * <p>Production açısından kritik olan ExecutorService davranışı sadece "kaç thread" değildir: queue'nun
 * SINIRLI olması ve dolduğunda ne olacağıdır. Burada {@link ArrayBlockingQueue} (sabit kapasite) ve
 * queue dolduğunda çağıranı bloklamadan task'ı REDDEDEN custom bir {@link RejectedExecutionHandler}
 * kullanılır. Bu, backpressure'ın en temel production tekniğidir: "daha fazla kaldıramıyorum" sinyalini
 * hemen ver, sessizce queue'da biriktirip memory'yi/latency'yi patlatma.
 *
 * <p>corePoolSize/maxPoolSize/queueCapacity request'ten DEĞİL config'ten gelir (concurrency-lab.bounded-executor):
 * bu sabitler bir business/kapasite kararıdır, her HTTP çağrısında keyfi değiştirilecek bir parametre değildir.
 */
@Service
public class BoundedThreadPoolLab {

    private static final Logger log = LoggerFactory.getLogger(BoundedThreadPoolLab.class);

    private final CustomerCheckSimulator customerCheck;
    private final ConcurrencyLabProperties.Safety safety;
    private final ConcurrencyLabProperties.BoundedExecutor config;

    public BoundedThreadPoolLab(CustomerCheckSimulator customerCheck, ConcurrencyLabProperties properties) {
        this.customerCheck = customerCheck;
        this.safety = properties.safety();
        this.config = properties.boundedExecutor();
    }

    public BoundedExecutorResponse run(int taskCount, long delayMs) {
        LabInputValidation.requireRange("taskCount", taskCount, 1, safety.maxBoundedTaskCount());
        LabInputValidation.requireRange("delayMs", delayMs, 0, safety.maxDelayMs());

        int core = config.corePoolSize();
        int max = config.maxPoolSize();
        int queueCapacity = config.queueCapacity();

        log.info("Bounded executor lab starting: taskCount={} core={} max={} queueCapacity={} delayMs={}",
                taskCount, core, max, queueCapacity, delayMs);

        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxActiveObserved = new AtomicInteger();
        AtomicInteger maxQueueObserved = new AtomicInteger();
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueCapacity);
        ThreadFactory factory = Thread.ofPlatform().name("bounded-pool-worker-", 0).factory();

        // Custom handler: varsayılan AbortPolicy execute()'tan RejectedExecutionException FIRLATIR.
        // Burada bilinçli olarak throw etmeyip SAYIYORUZ ki submission loop'u kesintiye uğramadan
        // "kaç task reddedildi" net bir metrik olarak gözlemlenebilsin (gerçek bir Spring controller'da
        // bu handler yerine execute() çağrısını try/catch'e almak da eşdeğer bir seçenektir).
        RejectedExecutionHandler rejectionHandler = (runnable, exec) -> {
            rejectedCount.incrementAndGet();
            log.warn("Task rejected: queue dolu (capacity={}), active={}", queueCapacity, exec.getActiveCount());
            // Reddedilen task asla çalışmayacağı için latch'i biz düşürüyoruz; aksi halde await() sonsuza kadar bekler.
            doneLatch.countDown();
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(core, max, 60, TimeUnit.SECONDS, queue, factory,
                rejectionHandler);

        long startNanos = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            int taskId = i;
            executor.execute(() -> runTask(taskId, delayMs, activeCount, maxActiveObserved, completed, doneLatch));
            acceptedCount.incrementAndGet();
            maxQueueObserved.getAndAccumulate(queue.size(), Math::max);
        }
        // acceptedCount şu an "reddedilmemiş submission" sayısıdır; rejectionHandler çalıştıysa düzeltiyoruz.
        acceptedCount.addAndGet(-rejectedCount.get());

        boolean finishedInTime = awaitDone(doneLatch);
        shutdownAndAwait(executor);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("Bounded executor lab finished: accepted={} rejected={} completed={} elapsedMs={} " +
                        "maxActiveObserved={} maxQueueObserved={}",
                acceptedCount.get(), rejectedCount.get(), completed.get(), elapsedMs, maxActiveObserved.get(),
                maxQueueObserved.get());

        String note = "unbounded queue (Lab 2) ile karşılaştırın: burada queue capacity=" + queueCapacity +
                " dolunca task'lar SESSİZCE birikmek yerine hemen reddedildi (backpressure). " +
                "rejected > 0 ise taskCount, core+queueCapacity'den fazla task'ı aynı anda üretti demektir.";
        return new BoundedExecutorResponse(taskCount, core, max, queueCapacity, acceptedCount.get(),
                rejectedCount.get(), completed.get(), maxActiveObserved.get(), maxQueueObserved.get(), elapsedMs,
                finishedInTime, note);
    }

    private void runTask(int taskId, long delayMs, AtomicInteger activeCount, AtomicInteger maxActiveObserved,
                          AtomicInteger completed, CountDownLatch doneLatch) {
        int now = activeCount.incrementAndGet();
        maxActiveObserved.getAndAccumulate(now, Math::max);
        try {
            customerCheck.check("order-" + taskId, delayMs);
            completed.incrementAndGet();
        } finally {
            activeCount.decrementAndGet();
            // Kabul edilmiş (reddedilmemiş) her task kendi countDown'ını burada yapar; reddedilenler
            // rejectionHandler içinde düşürülür. İkisi birlikte latch'in tam taskCount'a ulaşmasını sağlar.
            doneLatch.countDown();
        }
    }

    private boolean awaitDone(CountDownLatch doneLatch) {
        try {
            // Production'da sonsuza kadar beklemek istemeyiz; lab'ın güvenlik sınırları (taskCount<=200,
            // delayMs<=5000, core>=5) altında en kötü durumda bile bu süre yeterlidir.
            return doneLatch.await(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bounded executor lab interrupted while waiting for tasks", e);
        }
    }

    private void shutdownAndAwait(ThreadPoolExecutor executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
