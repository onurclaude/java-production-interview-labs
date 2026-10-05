package com.javalabs.pattern.common;

public record ApiErrorResponse(String error, String message) {

    public static ApiErrorResponse of(String error, String message) {
        return new ApiErrorResponse(error, message);
    }
}
