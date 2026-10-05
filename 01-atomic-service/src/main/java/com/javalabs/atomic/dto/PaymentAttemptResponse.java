package com.javalabs.atomic.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.javalabs.atomic.provider.ProviderChargeResult;

/**
 * Tek bir ödeme denemesinin sonucu.
 *
 * <p>Observation semantics: activeRequestsSeenAtDecision ve providerInFlightAtCall, bu thread'in
 * KENDİ karar/çağrı anında gördüğü değerlerdir. Diğer thread'ler aynı anda bu değerleri değiştirmektedir;
 * response client'a ulaştığında sistemin güncel durumu çoktan farklıdır. Bu alanlar "sistem şu an böyle"
 * değil, "bu request karar verirken bunu gördü" diye okunmalıdır. Global gözlem için /stats kullanılır.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentAttemptResponse(
        LabImplementation implementation,
        Outcome outcome,
        String paymentId,
        int limit,
        int activeRequestsSeenAtDecision,
        Integer providerInFlightAtCall,
        Integer casRetries,
        String thread,
        long durationMs,
        String observationNote) {

    private static final String NOTE =
            "Değerler bu thread'in karar anındaki snapshot'ıdır; global tutarlı state değildir. Genel durum için GET /api/labs/atomic/stats.";

    public enum Outcome {
        ACCEPTED,
        REJECTED
    }

    public static PaymentAttemptResponse accepted(LabImplementation implementation, String paymentId, int limit,
                                                  int seenAtDecision, Integer casRetries,
                                                  ProviderChargeResult providerResult, long startNanos) {
        return new PaymentAttemptResponse(implementation, Outcome.ACCEPTED, paymentId, limit, seenAtDecision,
                providerResult.providerInFlightAtStart(), casRetries,
                Thread.currentThread().getName(), elapsedMs(startNanos), NOTE);
    }

    public static PaymentAttemptResponse rejected(LabImplementation implementation, String paymentId, int limit,
                                                  int seenAtDecision, Integer casRetries, long startNanos) {
        return new PaymentAttemptResponse(implementation, Outcome.REJECTED, paymentId, limit, seenAtDecision,
                null, casRetries, Thread.currentThread().getName(), elapsedMs(startNanos), NOTE);
    }

    @JsonIgnore
    public boolean isRejected() {
        return outcome == Outcome.REJECTED;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
