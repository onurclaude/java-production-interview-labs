package com.javalabs.concurrency.coordination;

/**
 * simulateCrashWithoutFinally=true ise FraudCheck worker'ı, countDown()'ı try/finally İÇİNDE DEĞİL
 * normal akışın sonunda çağıran BAD bir implementasyonu simüle eder ve işi bitirmeden exception fırlatır.
 * Bu durumda latch asla sıfıra inmez; orchestrator awaitTimeoutMs sonunda timeout olur (sonsuza kadar beklemez).
 */
public record CountDownLatchRequest(
        long customerCheckDelayMs,
        long fraudCheckDelayMs,
        long pricingCheckDelayMs,
        boolean simulateCrashWithoutFinally,
        long awaitTimeoutMs) {
}
