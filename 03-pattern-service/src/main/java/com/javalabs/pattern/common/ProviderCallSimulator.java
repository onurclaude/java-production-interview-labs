package com.javalabs.pattern.common;

import org.springframework.stereotype.Component;

/**
 * Bütün lab'lardaki "external provider'ı çağırıyormuş gibi davran" ihtiyacını tek yerden karşılar.
 * Gerçek bir network call yoktur; sadece configured bir gecikme simüle edilir. Bu sınıf BİLİNÇLİ OLARAK
 * hiçbir pattern problemini "çözmez" — sadece lab'ların odağı olan pattern'i (Strategy/Adapter/Template/
 * Facade/Decorator) saf haliyle gözlemleyebilmemiz için ortak, basit bir yardımcıdır.
 */
@Component
public class ProviderCallSimulator {

    public void call(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider call simulation interrupted", e);
        }
    }
}
