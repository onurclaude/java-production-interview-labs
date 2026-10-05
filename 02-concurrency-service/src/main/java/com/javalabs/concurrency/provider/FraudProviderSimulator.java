package com.javalabs.concurrency.provider;

import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * External Fraud Provider'ın kontrollü simülasyonu — Lab 5 (BAD) ve Lab 6 (GOOD/Semaphore) için.
 *
 * <p>Gerçek hayatta böyle bir provider genelde API key/hesap bazlı bir "concurrent request" veya
 * "requests per second" limiti uygular (örn. ödeme/fraud sağlayıcılarının çoğu). Bu limit AŞILDIĞINDA
 * provider çoğunlukla 429/503 döner ya da bağlantıyı reddeder. Simulator bunu ProviderOverloadedException
 * ile taklit eder: limit aşılırsa çağrı BAŞARISIZ olur (01-atomic-service'teki PaymentProviderSimulator'ın
 * aksine — orada simulator sadece ihlali loglardı, burada bilinçli olarak gerçekten reddediyoruz, çünkü
 * bu lab'ın tam amacı "500 Virtual Thread oluşturabilmek, provider'ın 500'ü kaldırabileceği anlamına
 * gelmez" sonucunu gerçek bir hata ile göstermektir).
 */
@Component
public class FraudProviderSimulator {

    private static final Logger log = LoggerFactory.getLogger(FraudProviderSimulator.class);

    private final ConcurrencyObserver observer = new ConcurrencyObserver();
    private final int maxConcurrency;
    private final long delayMs;
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong overloadedCalls = new AtomicLong();

    public FraudProviderSimulator(ConcurrencyLabProperties properties) {
        this.maxConcurrency = properties.provider().fraud().maxConcurrency();
        this.delayMs = properties.provider().fraud().delayMs();
    }

    public void check(String orderId) {
        int now = observer.enter();
        totalCalls.incrementAndGet();
        try {
            if (now > maxConcurrency) {
                overloadedCalls.incrementAndGet();
                // Sadece yeni bir rekor kırıldığında WARN logla; aksi halde yük altında log spam oluşur.
                if (now == observer.observedMax()) {
                    log.warn("FRAUD PROVIDER OVERLOADED: inFlight={} > maxConcurrency={}", now, maxConcurrency);
                }
                throw new ProviderOverloadedException(
                        "Fraud provider overloaded: inFlight=%d > maxConcurrency=%d (order=%s)"
                                .formatted(now, maxConcurrency, orderId));
            }
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fraud provider call interrupted for order " + orderId, e);
        } finally {
            // Reddedilen çağrı da inFlight'a sayılmıştı (gerçek bir TCP bağlantısı/istek provider'a
            // ulaşıp orada reddedilmiş olurdu); finally ile ölçüm her durumda dengeleniyor.
            observer.exit();
        }
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    public FraudProviderStats stats() {
        int max = observer.observedMax();
        return new FraudProviderStats(maxConcurrency, observer.current(), max, totalCalls.get(),
                overloadedCalls.get(), max <= maxConcurrency);
    }

    public void resetObservations() {
        observer.reset();
        totalCalls.set(0);
        overloadedCalls.set(0);
    }
}
