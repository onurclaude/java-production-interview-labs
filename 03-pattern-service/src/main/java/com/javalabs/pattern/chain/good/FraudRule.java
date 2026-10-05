package com.javalabs.pattern.chain.good;

import com.javalabs.pattern.chain.CheckoutContext;
import com.javalabs.pattern.chain.CheckoutRule;
import com.javalabs.pattern.chain.RuleCheckResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class FraudRule implements CheckoutRule {

    @Override
    public RuleCheckResult check(CheckoutContext context) {
        if (context.simulateFraud()) {
            return RuleCheckResult.failed("Fraud signal detected");
        }
        return RuleCheckResult.passed("No fraud signal");
    }

    @Override
    public String ruleName() {
        return "FRAUD";
    }
}
