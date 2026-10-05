package com.javalabs.atomic.lab;

import com.javalabs.atomic.config.AtomicLabProperties;
import com.javalabs.atomic.dto.LabImplementation;
import com.javalabs.atomic.dto.PaymentAttemptResponse;
import com.javalabs.atomic.metrics.LabMetrics;
import com.javalabs.atomic.provider.PaymentProviderClient;
import com.javalabs.atomic.provider.ProviderChargeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * LAB 1 — BAD: Concurrency limit'i düz bir int ile tutmak.
 *
 * <p>Spring bean'leri varsayılan olarak singleton'dır. Tomcat her HTTP request'i ayrı bir thread'de
 * çalıştırır; yani bu sınıftaki activeRequests alanı aynı anda onlarca thread tarafından paylaşılır.
 */
@Service
public class PlainIntPaymentLab {

    private static final Logger log = LoggerFactory.getLogger(PlainIntPaymentLab.class);

    private final PaymentProviderClient provider;
    private final LabMetrics metrics;
    private final int limit;
    private final Duration raceWindow;

    /*
     * Normal int neden thread-safe değil? Üç ayrı problem birden var:
     *
     * 1) Check-then-act: "activeRequests < limit" kontrolü ile activeRequests++ ayrı adımlardır.
     *    20 thread aynı anda 19 okursa hepsi kontrolü geçer ve provider'a 20 değil 39 request gider.
     *
     * 2) Lost update: activeRequests++ tek bir işlem değildir; oku -> +1 -> yaz. İki thread 7 okuyup
     *    ikisi de 8 yazarsa bir artış kaybolur. Aynısı finally'deki activeRequests-- için de geçerli;
     *    yük sonrasında sayaç 0'a dönmeyebilir (pozitif ya da negatif kalabilir).
     *
     * 3) Visibility: alan volatile değil. Java Memory Model'e göre bir thread'in yazdığını diğerinin
     *    ne zaman göreceği garanti değildir (JIT değeri register'da tutabilir).
     *
     * volatile eklemek sadece 3'ü çözer; 1 ve 2 devam eder.
     */
    private int activeRequests;

    public PlainIntPaymentLab(PaymentProviderClient provider, LabMetrics metrics, AtomicLabProperties properties) {
        this.provider = provider;
        this.metrics = metrics;
        this.limit = properties.providerConcurrencyLimit();
        this.raceWindow = properties.raceWindow();
    }

    public PaymentAttemptResponse pay(boolean fail) {
        long start = System.nanoTime();
        String paymentId = "PAY-" + metrics.recordRequest();

        // CHECK
        int seen = activeRequests;
        if (seen >= limit) {
            metrics.recordRejected();
            log.info("REJECT {}: activeRequests={} >= limit={}", paymentId, seen, limit);
            return PaymentAttemptResponse.rejected(LabImplementation.PLAIN_INT, paymentId, limit, seen, null, start);
        }
        log.info("CHECK passed {}: read activeRequests={} (< {})", paymentId, seen, limit);

        // Bu bekleme production kodu değildir; race condition'ı deterministik şekilde görünür hale getirmek için lab amacıyla kullanılmıştır.
        // Bu pencerede diğer thread'ler de aynı (eski) değeri okuyup kontrolü geçer.
        LabOnlyDelay.widenRaceWindow(raceWindow);

        // ACT: kontrol anındaki değer artık geçerli değil; ama kod bunu bilmiyor.
        int afterIncrement = ++activeRequests;
        logAct(paymentId, seen, afterIncrement);
        metrics.recordAccepted();

        try {
            ProviderChargeResult result = provider.charge(paymentId, fail);
            return PaymentAttemptResponse.accepted(LabImplementation.PLAIN_INT, paymentId, limit, seen, null, result, start);
        } finally {
            activeRequests--;
        }
    }

    private void logAct(String paymentId, int seen, int afterIncrement) {
        if (afterIncrement > limit) {
            log.warn("LIMIT VIOLATED {}: check saw {}, after ++ value is {} > limit={}", paymentId, seen, afterIncrement, limit);
        } else {
            log.info("ACT {}: check saw {}, after ++ value is {}", paymentId, seen, afterIncrement);
        }
    }

    public int activeRequests() {
        return activeRequests;
    }

    void reset() {
        activeRequests = 0;
    }
}
