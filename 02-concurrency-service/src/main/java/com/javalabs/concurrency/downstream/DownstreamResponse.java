package com.javalabs.concurrency.downstream;

public record DownstreamResponse(
        String implementation,
        int requestedConcurrency,
        int providerMaxConcurrency,
        int accepted,
        int overloadedOrRejected,
        int maxObservedProviderConcurrency,
        boolean providerLimitRespected,
        long elapsedMs,
        String note) {
}
