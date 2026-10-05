package com.javalabs.concurrency.cow;

public record AddRuleResult(String name, boolean added, int size, String reason) {
}
