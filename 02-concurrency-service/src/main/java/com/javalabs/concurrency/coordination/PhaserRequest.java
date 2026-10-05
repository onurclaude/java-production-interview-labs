package com.javalabs.concurrency.coordination;

public record PhaserRequest(long validateDelayMs, long enrichDelayMs, long finalizeDelayMs) {
}
