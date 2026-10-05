package com.javalabs.pattern.chain.good;

import com.javalabs.pattern.chain.CheckoutContext;
import com.javalabs.pattern.chain.CheckoutRule;
import com.javalabs.pattern.chain.RuleCheckResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sıra 1: müşteri bloke mi? {@code @Order(1)} bu rule'un chain'deki konumunu bildirir — chain'in kendisi
 * "önce müşteriyi kontrol et" diye bir bilgi TAŞIMAZ, bu bilgi rule'un kendi annotation'ındadır.
 */
@Component
@Order(1)
public class CustomerStatusRule implements CheckoutRule {

    @Override
    public RuleCheckResult check(CheckoutContext context) {
        if (context.customerBlocked()) {
            return RuleCheckResult.failed("Customer is blocked");
        }
        return RuleCheckResult.passed("Customer in good standing");
    }

    @Override
    public String ruleName() {
        return "CUSTOMER_STATUS";
    }
}
