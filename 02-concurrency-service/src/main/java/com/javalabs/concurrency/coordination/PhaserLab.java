package com.javalabs.concurrency.coordination;

import com.javalabs.concurrency.common.LabInputValidation;
import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

/**
 * Lab 9 — Phaser.
 *
 * <p>PROBLEM: "Order Import Batch" — validate → enrich → finalize şeklinde art arda 3 fazdan geçen bir
 * toplu işlem. CyclicBarrier (Lab 8) bunu kolayca yapardı AMA bir şartla: parti (worker) sayısının HER
 * FAZDA AYNI kalması gerekir — CyclicBarrier kaç partiyle kurulduysa o sayıyı asla değiştiremez. Burada
 * ise worker-3, validate bittikten SONRA, sadece enrich fazına katılmak üzere SONRADAN işe giriyor; worker-0
 * ise enrich'ten sonra finalize'a girmeden işi bırakıyor. Parti sayısı fazdan faza DEĞİŞİYOR — bu
 * CyclicBarrier'ın kapsamı dışındadır.
 *
 * <p>ÇÖZÜM: {@code Phaser}, {@code register()} ile çalışma SIRASINDA yeni parti ekleyebilir,
 * {@code arriveAndDeregister()} ile bir partiyi gelecek fazlardan ÇIKARABİLİR:
 * <pre>
 *   Phase 0 (validate): worker-0, worker-1, worker-2 katılır
 *   -> hepsi bitirince    worker-3 register() ile EKLENİR
 *   Phase 1 (enrich):   worker-0, worker-1, worker-2, worker-3 katılır
 *   -> worker-0 VE worker-3 arriveAndDeregister() ile AYRILIR
 *   Phase 2 (finalize): SADECE worker-1, worker-2 katılır
 * </pre>
 * Phaser, her fazda kaç partinin "unarrived" olduğunu kendi içinde takip eder; diğer worker'ların bunu
 * bilmesine/yönetmesine gerek yoktur.
 *
 * <p>DİKKAT — dürüst not: gerçek bir Spring microservice'te Phaser GÜNLÜK kullanılan bir araç DEĞİLDİR.
 * Parti sayısı sabitse CyclicBarrier/CountDownLatch zaten yeterlidir ve çok daha basittir. Phaser'ı sadece
 * gerçekten "worker sayısı çalışma sırasında değişiyor" diyebileceğiniz (nadir) senaryolarda düşünün —
 * aksi halde gereksiz karmaşıklıktır.
 */
@Service
public class PhaserLab {

    private static final Logger log = LoggerFactory.getLogger(PhaserLab.class);

    private enum PhaseAction {
        AWAIT_ADVANCE,
        DEREGISTER
    }

    private record PhaseStep(String phaseName, long delayMs, PhaseAction action) {
    }

    private final ConcurrencyLabProperties.Safety safety;

    public PhaserLab(ConcurrencyLabProperties properties) {
        this.safety = properties.safety();
    }

    public PhaserResponse run(long validateDelayMs, long enrichDelayMs, long finalizeDelayMs) {
        LabInputValidation.requireRange("validateDelayMs", validateDelayMs, 0, safety.maxDelayMs());
        LabInputValidation.requireRange("enrichDelayMs", enrichDelayMs, 0, safety.maxDelayMs());
        LabInputValidation.requireRange("finalizeDelayMs", finalizeDelayMs, 0, safety.maxDelayMs());

        log.info("Phaser lab starting: validateDelay={} enrichDelay={} finalizeDelay={}",
                validateDelayMs, enrichDelayMs, finalizeDelayMs);

        List<String> events = Collections.synchronizedList(new ArrayList<>());
        // 3 sabit worker, phase 0 (validate) için kayıtlı başlar.
        Phaser phaser = new Phaser(3);
        // LAB ONLY koordinasyon: worker-0/1/2'nin "enrich" fazına girmesini, worker-3 register
        // edilene kadar DETERMİNİSTİK şekilde geciktirir. Bu olmadan register() ile diğer worker'ların
        // arrive çağrısı arasındaki sıra şansa bağlı olurdu.
        CountDownLatch dynamicWorkerRegisteredGate = new CountDownLatch(1);

        long startNanos = System.nanoTime();

        List<PhaseStep> fixedWorkerSteps = List.of(
                new PhaseStep("validate", validateDelayMs, PhaseAction.AWAIT_ADVANCE),
                new PhaseStep("enrich", enrichDelayMs, PhaseAction.AWAIT_ADVANCE),
                new PhaseStep("finalize", finalizeDelayMs, PhaseAction.DEREGISTER));
        List<PhaseStep> earlyLeaverSteps = List.of(
                new PhaseStep("validate", validateDelayMs, PhaseAction.AWAIT_ADVANCE),
                new PhaseStep("enrich", enrichDelayMs, PhaseAction.DEREGISTER));

        Thread w0 = Thread.ofVirtual().name("phaser-worker-0").start(
                () -> runWorker(0, earlyLeaverSteps, phaser, dynamicWorkerRegisteredGate, events, startNanos));
        Thread w1 = Thread.ofVirtual().name("phaser-worker-1").start(
                () -> runWorker(1, fixedWorkerSteps, phaser, dynamicWorkerRegisteredGate, events, startNanos));
        Thread w2 = Thread.ofVirtual().name("phaser-worker-2").start(
                () -> runWorker(2, fixedWorkerSteps, phaser, dynamicWorkerRegisteredGate, events, startNanos));

        // Ana thread Phaser'a KAYITLI DEĞİL (register olmadan da awaitAdvance kullanılabilir) — sadece
        // phase 0'ın (validate) bittiğini bekliyor, sonra dinamik worker'ı KAYIT EDİP gate'i açıyor.
        int phaseAfterValidate = phaser.awaitAdvance(0);
        events.add(timestamp(startNanos) + " MAIN: phase 0 (validate) complete (phase=" + phaseAfterValidate +
                "), registering dynamic worker-3 for phase 1 (enrich)");
        phaser.register();
        List<PhaseStep> dynamicWorkerSteps = List.of(new PhaseStep("enrich", enrichDelayMs, PhaseAction.DEREGISTER));
        Thread w3 = Thread.ofVirtual().name("phaser-worker-3-dynamic").start(
                () -> runWorker(3, dynamicWorkerSteps, phaser, null, events, startNanos));
        dynamicWorkerRegisteredGate.countDown();

        joinAll(w0, w1, w2, w3);
        long elapsedMs = elapsedMs(startNanos);

        log.info("Phaser lab finished: elapsedMs={} finalPhase={} unarrivedParties={}",
                elapsedMs, phaser.getPhase(), phaser.getUnarrivedParties());

        String note = "worker-3, phase 0 bittikten SONRA dinamik olarak register edildi (sadece enrich fazına " +
                "katıldı); worker-0 VE worker-3 enrich'ten sonra arriveAndDeregister() ile ayrıldı, finalize " +
                "fazına katılmadı. finalize fazını sadece worker-1 ve worker-2 tamamladı. " +
                "events listesindeki sıraya bakarak 'validate tamamlandı -> worker-3 register edildi -> enrich " +
                "başladı' sırasının HER ZAMAN korunduğunu doğrulayın.";
        return new PhaserResponse(new ArrayList<>(events), elapsedMs, note);
    }

    private void runWorker(int workerId, List<PhaseStep> steps, Phaser phaser, CountDownLatch gateAfterFirstPhase,
                            List<String> events, long startNanos) {
        for (int i = 0; i < steps.size(); i++) {
            PhaseStep step = steps.get(i);
            sleep(step.delayMs());
            events.add(timestamp(startNanos) + " worker-" + workerId + " finished " + step.phaseName());

            if (step.action() == PhaseAction.AWAIT_ADVANCE) {
                phaser.arriveAndAwaitAdvance();
            } else {
                // arriveAndDeregister: bu fazın arrival'ını sayar VE worker'ı GELECEK fazlardan çıkarır.
                // Kalan worker sayısı phaser tarafından otomatik düşürülür; diğer worker'lar bunu
                // ayrıca bilmek/yönetmek zorunda değildir.
                phaser.arriveAndDeregister();
            }
            events.add(timestamp(startNanos) + " worker-" + workerId + " arrived (" + step.action() +
                    ") after " + step.phaseName());

            if (i == 0 && gateAfterFirstPhase != null) {
                // Sadece 3 sabit worker (gate != null) burada bekler: worker-3'ün register edilmesini garanti eder.
                awaitGate(gateAfterFirstPhase);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitGate(CountDownLatch gate) {
        try {
            gate.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void joinAll(Thread... threads) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Phaser lab interrupted while waiting for workers", e);
            }
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String timestamp(long startNanos) {
        return "[+" + elapsedMs(startNanos) + "ms]";
    }
}
