package com.javalabs.atomic.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Atomic primitive'lerin en doğal production kullanımı: birbirinden bağımsız sayaçlar.
 *
 * <p>Neden AtomicLong burada uygun?
 * <ul>
 *   <li>Her sayaç tek başına anlamlıdır; "totalRequests artarken rejectedRequests de aynı anda tutarlı olmalı"
 *       gibi sayaçlar arası bir invariant yoktur. Bu yüzden her sayacın kendi içinde atomic olması yeterlidir.
 *       (Karşılaştırın: concurrency limit'te "kontrol et + artır" birlikte atomic olmak zorundaydı.)</li>
 *   <li>incrementAndGet() kayıp güncelleme (lost update) olmadan artırır; lock gerekmez.</li>
 *   <li>Kimse bu sayaçlara bakarak karar vermez; sadece gözlemlenir. Okunan değerin bir an sonra eskimesi sorun değildir.</li>
 * </ul>
 *
 * <p>Sınırlar (production'da bilinmesi gereken):
 * <ul>
 *   <li>Bu sayaçlar JVM-local'dir. 3 pod varsa 3 ayrı sayaç vardır; global toplamı ancak metrics sistemi
 *       (Prometheus/Micrometer vb.) pod'ları toplayarak verir. Pod restart olunca değerler sıfırlanır.
 *       Yani bu sınıf gerçek distributed observability sisteminin yerine geçmez; demo/metrics amaçlıdır.</li>
 *   <li>snapshot() sayaçları tek tek okur; dönen değerler aynı ana ait tutarlı bir fotoğraf değildir.</li>
 *   <li>Çok yüksek yazma contention'ında (çok çekirdek, saniyede milyonlarca artış) tek AtomicLong CAS
 *       çakışmaları üretir. Okumanın nadir olduğu böyle durumlarda LongAdder daha iyi ölçeklenir.
 *       Bu lab'da konu AtomicLong olduğu ve yük düşük olduğu için AtomicLong kullanıyoruz.</li>
 * </ul>
 */
@Component
public class LabMetrics {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong acceptedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();
    private final AtomicLong successfulPayments = new AtomicLong();
    private final AtomicLong failedPayments = new AtomicLong();
    private final AtomicLong casRetryCount = new AtomicLong();

    /**
     * Dönen değer aynı zamanda JVM içinde benzersiz bir sıra numarasıdır (paymentId üretiminde kullanılır).
     * Dikkat: 3 pod'da her pod 1'den başlar; bu numara global olarak benzersiz DEĞİLDİR.
     */
    public long recordRequest() {
        return totalRequests.incrementAndGet();
    }

    public void recordAccepted() {
        acceptedRequests.incrementAndGet();
    }

    public void recordRejected() {
        rejectedRequests.incrementAndGet();
    }

    public void recordSuccessfulPayment() {
        successfulPayments.incrementAndGet();
    }

    public void recordFailedPayment() {
        failedPayments.incrementAndGet();
    }

    public void recordCasRetry() {
        casRetryCount.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                totalRequests.get(),
                acceptedRequests.get(),
                rejectedRequests.get(),
                successfulPayments.get(),
                failedPayments.get(),
                casRetryCount.get());
    }

    public void reset() {
        totalRequests.set(0);
        acceptedRequests.set(0);
        rejectedRequests.set(0);
        successfulPayments.set(0);
        failedPayments.set(0);
        casRetryCount.set(0);
    }

    public record Snapshot(
            long totalRequests,
            long acceptedRequests,
            long rejectedRequests,
            long successfulPayments,
            long failedPayments,
            long casRetryCount) {
    }
}
