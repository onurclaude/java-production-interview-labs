package com.javalabs.concurrency.provider;

import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * "Order Processing" dünyasındaki genel bir blocking downstream çağrının (customer/pricing/shipping
 * tarzı bir DB sorgusu veya network call) kontrollü simülasyonu.
 *
 * <p>Lab 1 (Platform Thread), Lab 2 (FixedThreadPool), Lab 3 (Bounded Executor) ve Lab 4 (Virtual Thread)
 * BİLİNÇLİ olarak aynı simulator'ı, aynı delay'i kullanır: amaç dört thread modelini aynı iş yükü
 * üzerinde adil şekilde karşılaştırmaktır. Fraud provider'dan farkı: burada "maksimum concurrency" diye
 * sert bir iş kuralı yok, sadece gerçekçi bir blocking I/O gecikmesi var (bkz. FraudProviderSimulator).
 */
@Component
public class CustomerCheckSimulator {

    private static final Logger log = LoggerFactory.getLogger(CustomerCheckSimulator.class);

    private final ConcurrencyObserver observer = new ConcurrencyObserver();
    private final long defaultDelayMs;

    public CustomerCheckSimulator(ConcurrencyLabProperties properties) {
        this.defaultDelayMs = properties.customerCheck().defaultDelayMs();
    }

    /** delayOverrideMs <= 0 ise config'teki varsayılan gecikme kullanılır. */
    public void check(String orderId, long delayOverrideMs) {
        long delayMs = delayOverrideMs > 0 ? delayOverrideMs : defaultDelayMs;
        int now = observer.enter();
        try {
            // Gerçek bir DB sorgusu veya network call'ın süresini temsil eden LAB ONLY bekleme.
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            // Interrupt status'u yutmak yerine geri yüklüyoruz: çağıran kod (örn. executor shutdownNow)
            // bu thread'in interrupt edildiğini bir sonraki blocking çağrıda tekrar görebilmeli.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Customer check interrupted for order " + orderId, e);
        } finally {
            observer.exit();
        }
    }

    public int currentActive() {
        return observer.current();
    }

    public int observedMaxConcurrency() {
        return observer.observedMax();
    }

    public void resetObservations() {
        observer.reset();
        log.debug("CustomerCheckSimulator observations reset");
    }
}
