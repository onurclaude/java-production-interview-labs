package com.javalabs.pattern.chain.good;

import com.javalabs.pattern.chain.CheckoutContext;
import com.javalabs.pattern.chain.CheckoutRule;
import com.javalabs.pattern.chain.RuleCheckResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class StockRule implements CheckoutRule {

    @Override
    public RuleCheckResult check(CheckoutContext context) {
        if (!context.stockAvailable()) {
            return RuleCheckResult.failed("Insufficient stock");
        }
        return RuleCheckResult.passed("Stock available");
    }

    @Override
    public String ruleName() {
        return "STOCK";
    }
}
