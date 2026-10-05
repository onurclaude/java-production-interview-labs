package com.javalabs.concurrency.thread;

public record CpuBoundRequest(int taskCount, int workUnits, CpuBoundMode mode) {
}
