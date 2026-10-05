package com.javalabs.concurrency.provider;

public record FraudProviderStats(
        int maxConcurrency,
        int currentInFlight,
        int observedMaxConcurrency,
        long totalCalls,
        long overloadedCalls,
        boolean limitRespected) {
}
