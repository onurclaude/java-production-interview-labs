package com.javalabs.concurrency.executor;

public record BoundedExecutorRequest(int taskCount, long delayMs) {
}
