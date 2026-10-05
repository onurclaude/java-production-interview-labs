package com.javalabs.concurrency.thread;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU-bound vs I/O-bound karşılaştırması.
 *
 * <p>Burada HİÇBİR blocking I/O / Thread.sleep yoktur; her task saf CPU hesabı yapar (I/O yok, lock yok).
 * Virtual Thread'in asıl faydası blocking I/O'da carrier thread'i serbest bırakmasıdır; CPU-bound bir
 * işte serbest bırakılacak bir "blocking an" yoktur — thread CPU'da sürekli çalışır. Bu yüzden
 * VIRTUAL modunda da PLATFORM_POOL modunda da gerçek paralellik sınırı {@code availableProcessors()}'tır.
 * mode=VIRTUAL ile mode=PLATFORM_POOL arasında anlamlı bir elapsedMs farkı GÖRMEMEK beklenen sonuçtur;
 * bu lab'ın öğrettiği şey tam olarak budur ("Virtual Thread her şeyi hızlandırır" yanılgısının çürütülmesi).
 */
@Service
public class CpuBoundLab {

    private static final Logger log = LoggerFactory.getLogger(CpuBoundLab.class);

    private final ConcurrencyLabProperties.Safety safety;

    public CpuBoundLab(ConcurrencyLabProperties properties) {
        this.safety = properties.safety();
    }

    public CpuBoundResponse run(int taskCount, int workUnits, CpuBoundMode mode) {
        LabInputValidation.requireRange("taskCount", taskCount, 1, safety.maxCpuBoundTaskCount());
        LabInputValidation.requireRange("workUnits", workUnits, 1, safety.maxCpuBoundWorkUnits());

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        log.info("CPU-bound lab starting: mode={} taskCount={} workUnits={} availableProcessors={}",
                mode, taskCount, workUnits, availableProcessors);

        AtomicInteger completed = new AtomicInteger();
        ThreadFactory factory = mode == CpuBoundMode.VIRTUAL
                ? Thread.ofVirtual().name("cpu-vt-", 0).factory()
                : Thread.ofPlatform().name("cpu-platform-", 0).factory();

        long startNanos = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(taskCount);
        // PLATFORM_POOL modunda bilinçli olarak availableProcessors() kadar worker kullanılır:
        // CPU-bound işte çekirdek sayısından fazla thread açmak sadece context-switch maliyeti ekler,
        // throughput'u artırmaz (I/O-bound'daki "daha çok thread = daha çok eşzamanlı bekleme" mantığı burada geçerli değildir).
        try (ExecutorService executor = mode == CpuBoundMode.VIRTUAL
                ? Executors.newThreadPerTaskExecutor(factory)
                : Executors.newFixedThreadPool(availableProcessors, factory)) {
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    busyCompute(workUnits);
                    completed.incrementAndGet();
                }));
            }
            awaitAll(futures);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("CPU-bound lab finished: mode={} completed={}/{} elapsedMs={}", mode, completed.get(), taskCount,
                elapsedMs);

        String note = "CPU-bound işte paralellik sınırı availableProcessors()=" + availableProcessors +
                "'tır. mode=VIRTUAL kullanmak bu sınırı artırmaz; Virtual Thread'in kazancı sadece " +
                "blocking I/O'da carrier thread'i serbest bırakabilmesidir, CPU'da serbest bırakılacak bir an yoktur.";
        return new CpuBoundResponse(mode, taskCount, workUnits, completed.get(), elapsedMs, availableProcessors, note);
    }

    /** Saf CPU hesabı: I/O yok, sleep yok, lock yok. Deterministik (random yok). */
    private long busyCompute(int workUnits) {
        long acc = 0;
        for (int i = 1; i <= workUnits; i++) {
            acc += (long) Math.sqrt(i) ^ i;
        }
        return acc;
    }

    private void awaitAll(List<Future<?>> futures) {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("CPU-bound lab interrupted while waiting for tasks", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("CPU-bound task failed", e.getCause());
            }
        }
    }
}
