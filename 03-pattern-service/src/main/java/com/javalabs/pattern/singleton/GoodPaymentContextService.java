package com.javalabs.pattern.singleton;

import com.javalabs.pattern.common.PatternLabValidation;
import com.javalabs.pattern.config.PatternLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * BU SERVICE DE SINGLETON'DIR — bunu değiştirmiyoruz, Spring'in varsayılanı ve çoğu service için
 * doğru/tercih edilen kullanımdır (bkz. README "Singleton Hakkında Yanlış Sonuç Çıkarma"). Fark: bu
 * class'ta request'e özgü veri ({@code orderId}, {@code amount}) hiçbir ZAMAN bir FIELD'da tutulmaz —
 * SADECE method parametresi ve LOCAL variable olarak yaşar.
 *
 * <p>Java'da her method çağrısının local variable'ları, O ÇAĞRIYA ait kendi stack frame'inde yaşar.
 * İki thread AYNI method'u AYNI anda çağırsa bile, her biri KENDİ stack frame'ine, kendi
 * {@code orderId}/{@code amount} kopyasına sahiptir — bunlar PAYLAŞILMAZ. Bu yüzden
 * {@link BadPaymentContextService}'teki race condition burada YAPISAL OLARAK İMKANSIZDIR; holdMs ne
 * kadar büyük olursa olsun, kaç concurrent request gelirse gelsin, her request KENDİ gönderdiği veriyi
 * geri görür.
 */
@Service
public class GoodPaymentContextService {

    private static final Logger log = LoggerFactory.getLogger(GoodPaymentContextService.class);

    private final PatternLabProperties properties;

    public GoodPaymentContextService(PatternLabProperties properties) {
        this.properties = properties;
    }

    public SingletonResult process(String orderId, BigDecimal amount, long holdMs) {
        PatternLabValidation.requireRange("holdMs", holdMs, 0, properties.singletonLab().maxHoldMs());

        // orderId/amount burada LOCAL variable'dır (method parametresi) — bu stack frame'e özeldir,
        // başka hiçbir thread/request bunu göremez veya değiştiremez.
        log.info("[GOOD] processing orderId={} (thread={})", orderId, Thread.currentThread().getName());
        sleep(holdMs);
        log.info("[GOOD] still processing orderId={} after holdMs={} (local variable never changed)",
                orderId, holdMs);

        return new SingletonResult(orderId, orderId, amount, false, Thread.currentThread().getName());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Singleton GOOD lab interrupted", e);
        }
    }
}
