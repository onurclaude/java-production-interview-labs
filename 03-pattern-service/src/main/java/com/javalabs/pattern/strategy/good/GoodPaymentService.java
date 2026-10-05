package com.javalabs.pattern.strategy.good;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.strategy.PaymentStrategy;
import com.javalabs.pattern.strategy.PaymentStrategyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BU PATTERN NEYİ DEĞİŞTİRİYOR? {@code BadPaymentService.pay()} ile bu class'ın {@link #pay} method'unu
 * karşılaştırın: BAD'de her payment type'ın validasyon kuralı, provider adı, hata mesajı burada vardı.
 * Burada YOKTUR. Bu service'in TEK sorumluluğu: "doğru strategy'yi bul, ona delege et." Yeni bir
 * APPLE_PAY geldiğinde burada TEK BİR SATIR DEĞİŞMEZ — sadece yeni bir {@code ApplePayPaymentStrategy}
 * bean'i yazılır, {@link PaymentStrategyResolver} onu otomatik olarak Spring'den alıp registry'ye ekler.
 *
 * <p>Resolver seçimi YAPAR, strategy davranışı GERÇEKLEŞTİRİR, bu service ise İKİSİNİ BİRBİRİNE BAĞLAR —
 * üç ayrı sorumluluk, üç ayrı yerde.
 */
@Service
public class GoodPaymentService {

    private static final Logger log = LoggerFactory.getLogger(GoodPaymentService.class);

    private final PaymentStrategyResolver resolver;

    public GoodPaymentService(PaymentStrategyResolver resolver) {
        this.resolver = resolver;
    }

    public PaymentResult pay(PaymentCommand command) {
        PaymentStrategy strategy = resolver.resolve(command.paymentType());
        log.info("[GOOD] Resolved strategy {} for paymentType={}, delegating...",
                strategy.getClass().getSimpleName(), command.paymentType());
        return strategy.pay(command);
    }
}
