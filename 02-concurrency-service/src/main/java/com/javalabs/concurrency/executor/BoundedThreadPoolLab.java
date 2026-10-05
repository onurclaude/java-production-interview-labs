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
 * <p>PROBLEM: Lab 2'deki (FixedThreadPool) kuyruk SINIRSIZDI — kapasiteyi aşan her iş sessizce kuyrukta
 * birikiyordu. Sistem kapasitesinin ÜZERİNDE iş gelmeye devam ederse (arrival-rate > service-rate),
 * bu kuyruk sonsuza kadar RAM'de büyür. Bazen "daha fazla kaldıramıyorum" demek, sessizce biriktirmekten
 * daha sağlıklıdır — buna **backpressure** denir.
 *
 * <p>ÇÖZÜM: {@link ArrayBlockingQueue} (sabit kapasite, varsayılan 10) + bu kapasite dolduğunda yeni
 * task'ı hemen REDDEDEN custom bir {@link RejectedExecutionHandler}. Somut örnek (varsayılan config:
 * core=5, max=10, queue=10), 40 iş birden gelirse:
 * <pre>
 *   İş 1-5    -> core worker'lar hemen çalışmaya başlar (aktif: 5)
 *   İş 6-10   -> core dolu, yeni worker açılır (max'a kadar)    (aktif: 10)
 *   İş 11-20  -> worker'lar dolu (10/10), queue'ya girer         (queue: 10/10)
 *   İş 21-40  -> worker'lar VE queue dolu -> HEMEN REDDEDİLİR    (rejected: 20)
 * </pre>
 * Gerçek test sonucu tam olarak budur: {@code accepted=20, rejected=20}. Kabul edilen 20 = maxPoolSize(10)
 * + queueCapacity(10); fazlası reddedilir.
 *
 * <p>DİKKAT: {@code corePoolSize}/{@code maxPoolSize}/{@code queueCapacity} request'ten DEĞİL config'ten
 * gelir (`concurrency-lab.bounded-executor`): bunlar bir business/kapasite kararıdır, her HTTP çağrısında
 * keyfi değiştirilecek bir parametre değildir — gerçek bir sistemde de bu sayılar load-test'lerle belirlenir,
 * rastgele seçilmez.
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
