package com.javalabs.pattern.chain;

public record RuleCheckResult(boolean passed, String detail) {

    public static RuleCheckResult passed(String detail) {
        return new RuleCheckResult(true, detail);
    }

    public static RuleCheckResult failed(String detail) {
        return new RuleCheckResult(false, detail);
    }
}
