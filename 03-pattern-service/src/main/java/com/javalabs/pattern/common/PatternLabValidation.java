package com.javalabs.pattern.common;

public final class PatternLabValidation {

    private PatternLabValidation() {
    }

    public static void requireRange(String field, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "%s must be between %d and %d but was %d".formatted(field, min, max, value));
        }
    }

    public static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
