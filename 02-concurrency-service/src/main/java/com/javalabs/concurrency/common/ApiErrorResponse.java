package com.javalabs.concurrency.common;

public record ApiErrorResponse(String error, String message, String thread) {

    public static ApiErrorResponse of(String error, String message) {
        return new ApiErrorResponse(error, message, Thread.currentThread().getName());
    }
}
