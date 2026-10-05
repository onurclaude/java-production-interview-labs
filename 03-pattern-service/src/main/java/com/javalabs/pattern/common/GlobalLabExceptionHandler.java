package com.javalabs.pattern.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalLabExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalLabExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_INPUT", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(RuntimeException e) {
        log.error("Unexpected lab error", e);
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of("LAB_ERROR", e.getMessage()));
    }
}
