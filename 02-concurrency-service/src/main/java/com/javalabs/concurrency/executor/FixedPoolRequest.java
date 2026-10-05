package com.javalabs.concurrency.executor;

public record FixedPoolRequest(int taskCount, int poolSize, long delayMs) {
}
