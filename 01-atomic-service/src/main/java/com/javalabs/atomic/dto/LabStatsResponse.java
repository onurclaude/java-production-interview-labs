package com.javalabs.atomic.dto;

import com.javalabs.atomic.metrics.LabMetrics;
import com.javalabs.atomic.provider.ProviderStats;

/**
 * @param activeRequests her lab'ın KENDİ limit sayacı (uygulamanın "şu an kaç slot dolu sanıyorum" bilgisi).
 * @param metrics        JVM-local AtomicLong sayaçları.
 * @param provider       provider tarafının gözlemi (gerçekte kaç request aynı anda provider'daydı).
 */
public record LabStatsResponse(
        String instance,
        int limit,
        String providerMode,
        ActiveRequests activeRequests,
        LabMetrics.Snapshot metrics,
        ProviderStats provider,
        String note) {

    public record ActiveRequests(int plainInt, int checkThenAct, int cas, int leakBad, int leakGood) {
    }
}
