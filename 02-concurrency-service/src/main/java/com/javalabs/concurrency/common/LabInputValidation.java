package com.javalabs.concurrency.common;

/**
 * Lab endpoint'lerindeki request parametreleri için ortak aralık kontrolü.
 *
 * <p>Neden Bean Validation (@Min/@Max) değil? Bu, 01-atomic-service'teki tercihle aynıdır: tek bir
 * basit dependency (spring-boot-starter-web) ile idare etmek, validation-starter eklememek için bilinçli
 * bir karar. Bu kontroller DTO'nun kendi invariant'ı olmadığı (örn. taskCount her zaman pozitif OLMALI
 * değil, sadece "güvenlik sınırı" olduğu) için controller/service seviyesinde, record constructor'ında değil.
 */
public final class LabInputValidation {

    private LabInputValidation() {
    }

    public static void requireRange(String field, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "%s must be between %d and %d but was %d (bilgisayarı kilitlememek için güvenlik sınırı)"
                            .formatted(field, min, max, value));
        }
    }

    public static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
