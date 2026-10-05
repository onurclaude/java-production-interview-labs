package com.javalabs.pattern.strategy.good;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;
import com.javalabs.pattern.common.ProviderCallSimulator;
import com.javalabs.pattern.config.PatternLabProperties;
import com.javalabs.pattern.strategy.PaymentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Kredi kartı ödemesinin TÜM detayını (validasyon kuralı, provider çağrısı, sonuç mesajı) sadece bu
 * class bilir. {@code GoodPaymentService} ve {@code PaymentStrategyResolver} bu detaylardan HABERSİZDİR —
 * onlar için bu sadece "CREDIT_CARD'ı destekleyen bir PaymentStrategy"dir. Yarın kart validasyon kuralı
 * değişirse (örn. 3D Secure eklenirse), SADECE bu dosya değişir; diğer payment type'lar etkilenmez.
 */
@Component
public class CreditCardPaymentStrategy implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(CreditCardPaymentStrategy.class);

    private final ProviderCallSimulator providerCall;
    private final long providerDelayMs;

    public CreditCardPaymentStrategy(ProviderCallSimulator providerCall, PatternLabProperties properties) {
        this.providerCall = providerCall;
        this.providerDelayMs = properties.providerDelayMs();
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        if (command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Card payment amount must be positive");
        }
        log.info("[GOOD] CreditCardPaymentStrategy validating + charging for {}", command.orderId());
        providerCall.call(providerDelayMs);
        return PaymentResult.success(command.orderId(), "CARD_PROVIDER_SIMULATOR",
                "Card charged (GOOD: only this class knows card-specific rules)");
    }

    @Override
    public PaymentType supports() {
        return PaymentType.CREDIT_CARD;
    }
}
