package com.javalabs.concurrency.common;

import com.javalabs.concurrency.provider.ProviderOverloadedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bütün lab controller'ları için tek, ortak exception mapping noktası.
 * Her controller'a aynı iki @ExceptionHandler'ı kopyalamak yerine burada tek sefer tanımlanır.
 */
@RestControllerAdvice
public class GlobalLabExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalLabExceptionHandler.class);

    /**
     * Güvenlik sınırı ihlali veya geçersiz input -> 400. Bu bir "bug" değil, kullanıcı/client hatasıdır.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_INPUT", e.getMessage()));
    }

    /**
     * Downstream provider kapasitesi aşıldı -> 502 Bad Gateway. Hata bizim tarafımızda değil,
     * kontrolsüz şekilde çağırdığımız arkadaki sistemde (bkz. Lab 5 - uncontrolled virtual thread downstream).
     */
    @ExceptionHandler(ProviderOverloadedException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderOverloaded(ProviderOverloadedException e) {
        log.warn("Provider overloaded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiErrorResponse.of("PROVIDER_OVERLOADED", e.getMessage()));
    }

    /**
     * Beklenmeyen her şey için son çare. Lab kodunda yutulmuş bir interrupt veya başka bir bug'ı
     * sessizce 500'e gömmek yerine mesajı görünür bırakıyoruz (bu bir production API değil, bir lab).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(RuntimeException e) {
        log.error("Unexpected lab error", e);
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of("LAB_ERROR", e.getMessage()));
    }
}
