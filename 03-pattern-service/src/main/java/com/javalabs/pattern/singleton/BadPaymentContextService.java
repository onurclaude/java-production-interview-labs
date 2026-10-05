package com.javalabs.pattern.singleton;

import com.javalabs.pattern.common.PatternLabValidation;
import com.javalabs.pattern.config.PatternLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Lab 9 — BAD: Spring'in singleton bean scope'u ile "thread-safe" KAVRAMLARI KARIŞTIRILIYOR.
 *
 * <p>BUSINESS PROBLEM YOK — BU BİR BUG DEMOSU: {@code @Service} annotation'ı bu class'tan Spring
 * context'inde TEK BİR instance oluşturulmasını sağlar (singleton scope — Spring bean'lerinin
 * VARSAYILANI budur). Tomcat her HTTP request'i AYRI bir thread'de çalıştırır. Yani bu TEK instance,
 * AYNI ANDA gelen onlarca request thread'i tarafından PAYLAŞILIR.
 *
 * <p>BAD YAKLAŞIM: Bu class, request'e özgü veriyi ({@code currentOrderId}, {@code currentAmount})
 * BEAN'İN FIELD'INDA tutuyor. Adım adım, 2 concurrent request ile ne olur:
 * <pre>
 *   t=0ms   Request-A (Thread-1): currentOrderId = "ORDER-A"
 *   t=0ms   Request-A (Thread-1): holdMs boyunca BEKLER (henüz field'ı OKUMADI)
 *   t=50ms  Request-B (Thread-2): currentOrderId = "ORDER-B"   // AYNI bean, AYNI field!
 *   t=50ms  Request-B (Thread-2): hemen okur -> observedOrderId = "ORDER-B" (kendi yazdığı, doğru)
 *   t=2000ms Request-A (Thread-1): holdMs bitti, OKUR -> observedOrderId = "ORDER-B" (!!)
 * </pre>
 * Request-A kendi gönderdiği "ORDER-A"yı değil, Request-B'nin yazdığı "ORDER-B"yi görür. BU, BİR
 * RACE CONDITION'DIR ve iki request GERÇEKTEN aynı anda (biri diğerinin hold penceresi içinde) gelirse
 * HER ZAMAN reprodüklenir — şansa bağlı değildir (holdMs'i yeterince büyük tutmak, deterministik hale
 * getirir; aynı teknik 01-atomic-service'teki {@code race-window} ile birebir aynıdır).
 *
 * <p>KRİTİK NOKTA: Sorun "Spring singleton kullandık" DEĞİLDİR. Sorun, REQUEST'E ÖZGÜ, DEĞİŞKEN veriyi
 * PAYLAŞILAN bir bean'in FIELD'INDA tutmaktır. Bkz. {@link GoodPaymentContextService} — AYNI şekilde
 * singleton'dır, ama field'da hiçbir mutable request state tutmaz.
 */
@Service
public class BadPaymentContextService {

    private static final Logger log = LoggerFactory.getLogger(BadPaymentContextService.class);

    private final PatternLabProperties properties;

    // DİKKAT: Bu iki field, TEK bir bean instance'ına ait. Her request thread'i AYNI field'ları okuyup yazar.
    private String currentOrderId;
    private BigDecimal currentAmount;

    public BadPaymentContextService(PatternLabProperties properties) {
        this.properties = properties;
    }

    public SingletonResult process(String orderId, BigDecimal amount, long holdMs) {
        PatternLabValidation.requireRange("holdMs", holdMs, 0, properties.singletonLab().maxHoldMs());

        this.currentOrderId = orderId;
        this.currentAmount = amount;
        log.info("[BAD] field SET: currentOrderId={} (thread={})", currentOrderId, Thread.currentThread().getName());

        // LAB ONLY: production kodu değildir. Bu bekleme, "field'a yazdım ama henüz okumadım" penceresini
        // deterministik şekilde genişletir — ki başka bir concurrent request bu pencerede araya girip
        // field'ları ezebilsin. Aynı 01-atomic-service'teki race-window prensibi: bekleme bug'ı YARATMAZ,
        // zaten var olan (normalde mikrosaniyelik) pencereyi gözlemlenebilir hale getirir.
        sleep(holdMs);

        String observedOrderId = this.currentOrderId;
        BigDecimal observedAmount = this.currentAmount;
        boolean corrupted = !orderId.equals(observedOrderId);

        log.info("[BAD] field READ after holdMs={}: observedOrderId={} (requested={}) corrupted={}",
                holdMs, observedOrderId, orderId, corrupted);

        return new SingletonResult(orderId, observedOrderId, observedAmount, corrupted,
                Thread.currentThread().getName());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Singleton BAD lab interrupted", e);
        }
    }
}
