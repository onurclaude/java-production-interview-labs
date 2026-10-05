package com.javalabs.pattern.chain.good;

import com.javalabs.pattern.chain.CheckoutContext;
import com.javalabs.pattern.chain.CheckoutRule;
import com.javalabs.pattern.chain.RuleCheckResult;
import com.javalabs.pattern.chain.RuleOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BU PATTERN NEYİ DEĞİŞTİRİYOR? {@code BadCheckoutValidationService.validate()} ile {@link #run} method'unu
 * karşılaştırın: BAD'de orchestrator her rule'un mantığını VE fail-fast davranışını biliyordu. Burada
 * chain, rule'ların İÇİNDE ne kontrol ettiğini HİÇ BİLMEZ — sadece Spring'in {@code List<CheckoutRule>}
 * enjeksiyonunda {@code @Order} sırasına göre dizilmiş rule'ları SIRAYLA çalıştırır, biri {@code FAILED}
 * derse DURUR. Yeni bir rule eklemek (örn. "VIP müşteri limit muafiyeti") sadece yeni bir
 * {@code @Component @Order(5)} class'ı yazmayı gerektirir — bu class HİÇ değişmez.
 *
 * <p>SPRING'İN {@code List<CheckoutRule>}'U SIRALI ENJEKTE ETMESİ: Spring, bir collection-type dependency
 * (burada {@code List<CheckoutRule>}) enjekte ederken, bean'ler üzerindeki {@code @Order} (veya
 * {@code Ordered} interface) değerine göre SIRALI bir liste verir. Bu yüzden rule'ların sırası, chain
 * class'ının İÇİNDE değil, HER rule'un KENDİ üzerindeki {@code @Order} annotation'ındadır — sıra
 * bilgisi, sıraya ihtiyacı olan class'ın (rule) kendisinde yaşar.
 *
 * <p>PRODUCTION'DA DİKKAT: Chain of Responsibility, Spring Security'nin {@code SecurityFilterChain}'i
 * veya Servlet {@code FilterChain}'i ile AYNI kavramsal fikri paylaşır: bağımsız parçalar sırayla
 * çalışır, biri "burada dur" diyebilir, sonraki parçalar o zaman çalışmaz.
 */
@Component
public class CheckoutRuleChain {

    private static final Logger log = LoggerFactory.getLogger(CheckoutRuleChain.class);

    private final List<CheckoutRule> rules;

    public CheckoutRuleChain(List<CheckoutRule> rules) {
        this.rules = rules;
        log.info("CheckoutRuleChain initialized with {} ordered rules: {}", rules.size(),
                rules.stream().map(CheckoutRule::ruleName).toList());
    }

    public List<RuleOutcome> run(CheckoutContext context) {
        List<RuleOutcome> outcomes = new ArrayList<>();
        boolean stopped = false;

        for (int i = 0; i < rules.size(); i++) {
            CheckoutRule rule = rules.get(i);
            int order = i + 1;

            if (stopped) {
                // Fail-fast: bir önceki rule FAILED olduğu için buradan sonrası hiç ÇALIŞTIRILMAZ,
                // sadece gözlem amaçlı SKIPPED olarak işaretlenir.
                outcomes.add(new RuleOutcome(order, rule.ruleName(), "SKIPPED", "Not evaluated (fail-fast)"));
                continue;
            }

            RuleCheckResult result = rule.check(context);
            outcomes.add(new RuleOutcome(order, rule.ruleName(), result.passed() ? "PASSED" : "FAILED",
                    result.detail()));
            if (!result.passed()) {
                log.info("[GOOD] Chain stopped at rule={} (order={}): {}", rule.ruleName(), order, result.detail());
                stopped = true;
            }
        }
        return outcomes;
    }
}
