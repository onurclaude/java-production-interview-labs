package com.javalabs.atomic.provider;

/**
 * External provider'ın hata döndürdüğü / ulaşılamadığı durum.
 * Lab'larda bu exception'ın slot release akışını nasıl atlayabildiğini (counter leak) gözlemliyoruz.
 */
public class ProviderFailureException extends RuntimeException {

    public ProviderFailureException(String message) {
        super(message);
    }

    public ProviderFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
