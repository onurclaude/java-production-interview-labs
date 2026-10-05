package com.javalabs.pattern.strategy;

import com.javalabs.pattern.common.PaymentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BUSINESS PROBLEM: {@link PaymentStrategy} davranışı NASIL yapılacağını bilir, ama birini SEÇMEK gerekir.
 * Bunu "manual factory" ile çözmek isteyebilirsiniz:
 * <pre>
 *   switch (type) {
 *       case CREDIT_CARD -> new CreditCardPaymentStrategy(...);
 *       ...
 *   }
 * </pre>
 * Bu BAD'dir çünkü {@code CreditCardPaymentStrategy} bir Spring bean'idir: dependency alabilir
 * (provider client, config, metrics). {@code new} ile oluşturursanız Spring'in dependency injection'ını
 * ve bean lifecycle'ını (örn. {@code @PostConstruct}, proxy'ler, AOP) BYPASS edersiniz — strategy'nin
 * constructor'ındaki her dependency'yi manuel olarak siz geçirmek zorunda kalırsınız.
 *
 * <p>BU SINIF NEYİ DEĞİŞTİRİYOR? Spring'in kendisi zaten her {@link PaymentStrategy} implementation'ını
 * bean olarak oluşturup yönetiyor. Bu resolver, Spring'in enjekte ettiği {@code List<PaymentStrategy>}'yi
 * alıp {@code PaymentType -> PaymentStrategy} şeklinde bir Map'e dönüştürür ({@link #resolve}). Hiçbir yerde
 * {@code new XxxStrategy()} YOKTUR — her şey Spring-managed bean'dir.
 *
 * <p>UYGULAMA BAŞLARKEN ADIM ADIM NE OLUR?
 * <pre>
 *   1) Spring context başlar, @Component olan CreditCardPaymentStrategy, WalletPaymentStrategy,
 *      BankTransferPaymentStrategy bean'leri oluşturulur (her biri kendi dependency'lerini alır).
 *   2) Bu resolver constructor'ında Spring, List&lt;PaymentStrategy&gt; parametresine BU 3 bean'i enjekte eder.
 *   3) Constructor, listeyi supports() değerine göre bir Map'e indexler.
 *   4) CREDIT_CARD içeren bir request geldiğinde: resolve(CREDIT_CARD) -> Map'ten O(1) lookup ->
 *      CreditCardPaymentStrategy bean'i döner.
 *   5) Çağıran taraf (GoodPaymentService) bu strategy'ye pay(command) der; NASIL yapıldığını bilmez.
 * </pre>
 */
@Component
public class PaymentStrategyResolver {

    private static final Logger log = LoggerFactory.getLogger(PaymentStrategyResolver.class);

    private final Map<PaymentType, PaymentStrategy> strategiesByType;

    public PaymentStrategyResolver(List<PaymentStrategy> strategies) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentStrategy::supports, Function.identity()));
        log.info("PaymentStrategyResolver initialized with {} Spring-managed strategies: {}",
                strategiesByType.size(), strategiesByType.keySet());
    }

    public PaymentStrategy resolve(PaymentType type) {
        PaymentStrategy strategy = strategiesByType.get(type);
        if (strategy == null) {
            // Gerçek production'da bu, "yeni bir PaymentType eklendi ama karşılığında strategy bean'i
            // yazılmadı/register edilmedi" demektir — deployment-time değil, runtime'da patlayan bir bug.
            throw new IllegalArgumentException("No PaymentStrategy registered for type " + type);
        }
        return strategy;
    }
}
