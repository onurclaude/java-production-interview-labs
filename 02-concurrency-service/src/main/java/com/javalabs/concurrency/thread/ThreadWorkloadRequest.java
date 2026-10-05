package com.javalabs.concurrency.thread;

/** delayMs <= 0 verilirse simulator'ın config'teki varsayılan gecikmesi kullanılır. */
public record ThreadWorkloadRequest(int taskCount, long delayMs) {
}
