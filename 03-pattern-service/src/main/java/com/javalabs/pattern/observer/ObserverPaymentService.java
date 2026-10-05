package com.javalabs.pattern.observer;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.ProviderCallSimulator;
import com.javalabs.pattern.config.PatternLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Lab 7 — Observer.
 *
 * <p>BUSINESS PROBLEM: Ödeme tamamlandığında 3 bağımsız şey olmalı: email bildirimi, audit kaydı,
 * analytics tracking. BAD yaklaşım, bu servisin İÇİNDE doğrudan
 * {@code emailService.send(); auditService.write(); analyticsService.track();} çağırmaktır — bu durumda
 * {@code ObserverPaymentService}, ödemeyle hiçbir ilgisi olmayan 3 ayrı subsystem'i BİLMEK ve
 * YÖNETMEK zorunda kalır. Yeni bir side effect (örn. "LoyaltyPointsListener") eklemek bu servisi
 * DEĞİŞTİRMEYİ gerektirir.
 *
 * <p>BU PATTERN NEYİ DEĞİŞTİRİYOR? Bu servis ödeme işini bitirince SADECE
 * {@code publisher.publishEvent(event)} der — "bu event'i kim dinliyor, kaç dinleyici var" bilgisinden
 * TAMAMEN habersizdir. {@link PaymentNotificationListener}, {@link PaymentAuditListener},
 * {@link PaymentAnalyticsListener} bağımsız olarak bu event'i dinler. Yeni bir listener eklemek bu
 * servisi HİÇ etkilemez.
 *
 * <p>ÇOK ÖNEMLİ — BU BİR KAFKA DEĞİL: {@code ApplicationEventPublisher}, AYNI JVM/process İÇİNDE
 * çalışan, IN-PROCESS bir event mekanizmasıdır. Varsayılan olarak SENKRONDUR: {@code publishEvent()}
 * çağrısı, TÜM listener'lar çalışıp bitene kadar BLOKE olur (bu lab'da BİLİNÇLİ olarak senkron
 * bırakılmıştır, ki pattern'in akışı net görülsün). Kafka gibi durable, distributed bir messaging
 * sistemi DEĞİLDİR: uygulama bu event publish edildikten hemen sonra çökerse ve bir listener henüz
 * çalışmadıysa, o listener'ın işi KAYBOLUR — disk'e yazılmış bir mesaj kuyruğu yoktur, retry/replay
 * garantisi yoktur (bkz. README "Observer vs Kafka").
 */
@Service
public class ObserverPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ObserverPaymentService.class);

    private final ApplicationEventPublisher publisher;
    private final ProviderCallSimulator providerCall;
    private final long providerDelayMs;

    public ObserverPaymentService(ApplicationEventPublisher publisher, ProviderCallSimulator providerCall,
                                  PatternLabProperties properties) {
        this.publisher = publisher;
        this.providerCall = providerCall;
        this.providerDelayMs = properties.providerDelayMs();
    }

    public PaymentResult completePayment(PaymentCommand command) {
        providerCall.call(providerDelayMs);

        PaymentCompletedEvent event = new PaymentCompletedEvent(command.orderId(), command.amount(),
                command.paymentType().name(), Instant.now());
        log.info("[OBSERVER] Payment completed for {}, publishing event (synchronous)", command.orderId());
        // publishEvent SENKRONDUR: aşağıdaki satıra geçmeden önce TÜM @EventListener'lar sırayla çalışır.
        publisher.publishEvent(event);

        return PaymentResult.success(command.orderId(), "OBSERVER_LAB_PROVIDER",
                "Payment completed, event published to 3 independent listeners");
    }
}
