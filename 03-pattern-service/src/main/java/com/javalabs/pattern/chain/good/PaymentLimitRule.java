package com.javalabs.pattern.chain.good;

import com.javalabs.pattern.chain.CheckoutContext;
import com.javalabs.pattern.chain.CheckoutRule;
import com.javalabs.pattern.chain.RuleCheckResult;
import com.javalabs.pattern.config.PatternLabProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class PaymentLimitRule implements CheckoutRule {

    private final PatternLabProperties properties;

    public PaymentLimitRule(PatternLabProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuleCheckResult check(CheckoutContext context) {
        if (context.amount().compareTo(properties.checkoutLimitAmount()) > 0) {
            return RuleCheckResult.failed("Amount exceeds limit " + properties.checkoutLimitAmount());
        }
        return RuleCheckResult.passed("Amount within limit");
    }

    @Override
    public String ruleName() {
        return "PAYMENT_LIMIT";
    }
}
