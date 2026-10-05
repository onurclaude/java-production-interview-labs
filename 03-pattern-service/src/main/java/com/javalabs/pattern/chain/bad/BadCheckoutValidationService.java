package com.javalabs.pattern.chain.bad;

import com.javalabs.pattern.chain.CheckoutContext;
import com.javalabs.pattern.chain.RuleOutcome;
import com.javalabs.pattern.config.PatternLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lab 2 — BAD: orchestrator, 4 kontrolün HEPSİNİ sıralı method çağrısıyla kendi içinde yönetiyor.
 *
 * <p>BUSINESS PROBLEM: Checkout tamamlanmadan önce customer durumu, fraud, ödeme limiti, stok sırayla
 * kontrol edilmeli; biri başarısız olursa kalanlar ÇALIŞMAMALI (fail-fast).
 *
 * <p>BAD YAKLAŞIM: Bu yaklaşımın problemi "4 method çağırmak" değildir — 4 sabit, hiç değişmeyecek
 * kontrol için bu gayet okunabilir. Problem, bu orchestrator'ın HER rule'un: hangi sırada çalıştığını,
 * hangisinin fail-fast olduğunu, hangi koşulda çalıştığını TEK TEK BİLMESİDİR. Yeni bir kontrol (örn.
 * "VIP müşteri için limit kontrolünü atla") eklemek bu method'un GÖVDESİNİ değiştirmeyi gerektirir —
 * ve bu method büyüdükçe, "hangi if hangi rule'a ait" takip etmek zorlaşır.
 */
@Service
public class BadCheckoutValidationService {

    private static final Logger log = LoggerFactory.getLogger(BadCheckoutValidationService.class);

    private final PatternLabProperties properties;

    public BadCheckoutValidationService(PatternLabProperties properties) {
        this.properties = properties;
    }

    public List<RuleOutcome> validate(CheckoutContext context) {
        List<RuleOutcome> outcomes = new ArrayList<>();

        // Orchestrator BURADA her rule'un sırasını, mantığını ve fail-fast davranışını KENDİSİ yönetiyor.
        if (context.customerBlocked()) {
            outcomes.add(new RuleOutcome(1, "CUSTOMER_STATUS", "FAILED", "Customer is blocked"));
            markRemainingSkipped(outcomes);
            return outcomes;
        }
        outcomes.add(new RuleOutcome(1, "CUSTOMER_STATUS", "PASSED", "Customer in good standing"));

        if (context.simulateFraud()) {
            outcomes.add(new RuleOutcome(2, "FRAUD", "FAILED", "Fraud signal detected"));
            markRemainingSkipped(outcomes);
            return outcomes;
        }
        outcomes.add(new RuleOutcome(2, "FRAUD", "PASSED", "No fraud signal"));

        if (context.amount().compareTo(properties.checkoutLimitAmount()) > 0) {
            outcomes.add(new RuleOutcome(3, "PAYMENT_LIMIT", "FAILED",
                    "Amount exceeds limit " + properties.checkoutLimitAmount()));
            markRemainingSkipped(outcomes);
            return outcomes;
        }
        outcomes.add(new RuleOutcome(3, "PAYMENT_LIMIT", "PASSED", "Amount within limit"));

        if (!context.stockAvailable()) {
            outcomes.add(new RuleOutcome(4, "STOCK", "FAILED", "Insufficient stock"));
            return outcomes;
        }
        outcomes.add(new RuleOutcome(4, "STOCK", "PASSED", "Stock available"));

        log.info("[BAD] All checkout rules passed for {}", context.orderId());
        return outcomes;
    }

    private void markRemainingSkipped(List<RuleOutcome> outcomes) {
        // Fail-fast'i her return noktasında elle tekrarlamak zorunda kaldık — bu da BAD'in bir parçası:
        // davranış (skip the rest) 3 farklı yerde COPY-PASTE edilmiş durumda.
        int next = outcomes.size() + 1;
        String[] remainingNames = {"FRAUD", "PAYMENT_LIMIT", "STOCK"};
        for (int i = outcomes.size(); i < 4; i++) {
            outcomes.add(new RuleOutcome(next++, remainingNames[i - 1], "SKIPPED", "Not evaluated (fail-fast)"));
        }
    }
}
