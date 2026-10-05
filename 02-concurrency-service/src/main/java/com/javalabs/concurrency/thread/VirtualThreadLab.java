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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab 4 — Virtual Thread.
 *
 * <p>Platform Thread lab'ı ile AYNI simulator (CustomerCheckSimulator), AYNI delay modeli kullanılır —
 * amaç adil bir karşılaştırma yapmaktır. Tek fark thread modelidir: her task kendi Virtual Thread'inde
 * çalışır. Virtual Thread'ler blocking çağrı (burada Thread.sleep) sırasında carrier (platform) thread'i
 * BIRAKIR; bu yüzden binlerce blocking task, binlerce OS thread'i meşgul etmeden ifade edilebilir.
 *
 * <p>Bilinçli tasarım notu: Spring Boot'un {@code spring.threads.virtual.enabled} ayarı tüm uygulamanın
 * (örn. Tomcat request handling) thread modelini global olarak değiştirebilir. Burada bunu KULLANMIYORUZ;
 * Platform/Fixed/Virtual farkını endpoint bazında açıkça göstermek için executor'ı bilinçli ve görünür
 * şekilde kendimiz yönetiyoruz (bkz. README "Spring Boot + Virtual Thread").
 */
@Service
public class VirtualThreadLab {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadLab.class);

    private final CustomerCheckSimulator customerCheck;
    private final ConcurrencyLabProperties.Safety safety;

    public VirtualThreadLab(CustomerCheckSimulator customerCheck, ConcurrencyLabProperties properties) {
        this.customerCheck = customerCheck;
        this.safety = properties.safety();
    }

    public ThreadWorkloadResponse run(int taskCount, long delayMs) {
        LabInputValidation.requireRange("taskCount", taskCount, 1, safety.maxVirtualTaskCount());
        LabInputValidation.requireRange("delayMs", delayMs, 0, safety.maxDelayMs());

        log.info("Virtual Thread lab starting: taskCount={} delayMs={}", taskCount, delayMs);

        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean allVirtual = new AtomicBoolean(true);

        // newVirtualThreadPerTaskExecutor() ile aynı şeydir; isimli bir ThreadFactory kullanarak
        // log/debug çıktısında "vt-task-N" gibi okunabilir thread adları elde ediyoruz.
        ThreadFactory virtualFactory = Thread.ofVirtual().name("vt-task-", 0).factory();

        long startNanos = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(taskCount);
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(virtualFactory)) {
            for (int i = 0; i < taskCount; i++) {
                int taskId = i;
                futures.add(executor.submit(
                        () -> runTask(taskId, delayMs, activeCount, maxObserved, completed, allVirtual)));
            }
            // try-with-resources close(): yeni submit kabul etmeyi durdurur ve TÜM task'lar bitene kadar bloklar.
            // Bu yüzden futures'ı burada açıkça bekletmek zorunlu değil, ama hata yakalamak için get() çağırıyoruz.
            awaitAll(futures);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("Virtual Thread lab finished: completed={}/{} elapsedMs={} maxObservedConcurrency={} allVirtual={}",
                completed.get(), taskCount, elapsedMs, maxObserved.get(), allVirtual.get());

        String note = "Virtual Thread'ler blocking I/O sırasında carrier thread'i serbest bırakır; bu yüzden " +
                "maxObservedConcurrency ~= taskCount olabilir (hepsi 'aynı anda' blocking I/O'da görünür). " +
                "Bu CPU-bound bir hız artışı DEĞİLDİR — bkz. /api/labs/threads/cpu-bound.";
        return new ThreadWorkloadResponse(ThreadModel.VIRTUAL_THREAD, taskCount, completed.get(), elapsedMs,
                allVirtual.get(), maxObserved.get(), note);
    }

    private void runTask(int taskId, long delayMs, AtomicInteger activeCount, AtomicInteger maxObserved,
                          AtomicInteger completed, AtomicBoolean allVirtual) {
        ThreadSnapshot snap = ThreadSnapshot.current();
        if (!snap.virtual()) {
            allVirtual.set(false);
        }
        int now = activeCount.incrementAndGet();
        maxObserved.getAndAccumulate(now, Math::max);
        try {
            if (log.isDebugEnabled()) {
                log.debug("task={} thread={} id={} virtual={} activeNow={}", taskId, snap.name(), snap.id(),
                        snap.virtual(), now);
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
                throw new IllegalStateException("Virtual thread lab interrupted while waiting for tasks", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Virtual thread task failed", e.getCause());
            }
        }
    }
}
