package com.javalabs.atomic.dto;

public record ProviderFailureResponse(
        String outcome,
        String path,
        String error,
        String thread,
        String hint) {
}
