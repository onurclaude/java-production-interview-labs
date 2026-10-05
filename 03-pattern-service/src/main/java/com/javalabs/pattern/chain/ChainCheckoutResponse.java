package com.javalabs.pattern.chain;

import java.util.List;

public record ChainCheckoutResponse(String lab, String orderId, boolean allPassed, List<RuleOutcome> executedRules) {
}
