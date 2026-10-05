package com.javalabs.concurrency.provider;

/**
 * External provider'ın kabul edebileceği concurrent request sayısı aşıldığında fırlatılır.
 * Lab 5 (uncontrolled virtual thread downstream) bunu gerçekten tetikler; Lab 6 (Semaphore) bunu önler.
 */
public class ProviderOverloadedException extends RuntimeException {

    public ProviderOverloadedException(String message) {
        super(message);
    }
}
