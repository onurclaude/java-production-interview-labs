package com.javalabs.concurrency.downstream;

/**
 * useTimeout=false ise GOOD lab {@code Semaphore.acquire()} (sınırsız bekleme) kullanır.
 * useTimeout=true ise {@code Semaphore.tryAcquire(timeoutMs, MILLISECONDS)} kullanır.
 * BAD lab (uncontrolled) bu alanları yok sayar.
 */
public record DownstreamRequest(int requestCount, boolean useTimeout, long timeoutMs) {
}
