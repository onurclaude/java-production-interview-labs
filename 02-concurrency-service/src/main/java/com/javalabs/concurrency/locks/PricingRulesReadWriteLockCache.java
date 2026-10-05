package com.javalabs.concurrency.locks;

import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lab 10 — ReadWriteLock: "Local Pricing Rules Cache".
 *
 * <p>PROBLEM: Pricing rule'ları SANİYEDE BİNLERCE kez okunuyor (her order fiyat kontrolü yapıyor), ama
 * sadece birkaç DAKİKADA bir reload ediliyor (bir config güncellemesinde). Normal bir {@code synchronized}
 * blok kullansaydık ne olurdu? Okuma bile exclusive bir kilit isterdi — yani:
 * <pre>
 *   Reader-A lock alır, okur, bırakır
 *   Reader-B bu sırada (A hâlâ okurken) GEREKSİZ YERE bekler  -- ikisi de sadece OKUYOR, çakışma yok!
 * </pre>
 * Oysa iki reader'ın aynı anda okuması TAMAMEN GÜVENLİDİR (hiçbiri veriyi değiştirmiyor). synchronized
 * bunu AYIRT EDEMEZ, okuma da yazma da aynı kilide girer.
 *
 * <p>ÇÖZÜM: {@code ReentrantReadWriteLock} okuma ile yazmayı AYRI kilitler olarak ele alır:
 * <pre>
 *   Reader-A readLock() alır  -> girer
 *   Reader-B readLock() alır  -> A'yı BEKLEMEZ, O DA GİRER (okuma paylaşımlı/shared)
 *   Reader-C readLock() alır  -> O DA GİRER
 *   Writer (reload) writeLock() ister -> A, B, C bitene kadar BEKLER (yazma exclusive)
 *   Writer yazarken yeni gelen Reader-D -> Writer bitene kadar BEKLER
 * </pre>
 * Gerçek test: 5 paralel GET isteği gönderildiğinde {@code maxObservedConcurrentReaders=4} gözlemlendi —
 * yani reader'lar GERÇEKTEN aynı anda içeride.
 *
 * <p>DİKKAT — ConcurrentHashMap yeterli olmaz mıydı? Tek bir key'in get/put'u ConcurrentHashMap ile zaten
 * thread-safe'tir. Ama burada ihtiyaç "TÜM price map'ini TEK SEFERDE, ATOMİK olarak değiştirmek" (reload
 * sırasında bir reader'ın YARI GÜNCELLENMİŞ bir map görmemesi). Reload "yeni bir map hazırla, sonra
 * referansı değiştir" şeklinde çalışır; tek bir {@code ConcurrentHashMap.replaceAll()} çağrısı bunu garanti
 * etmez. Asıl kazanç, map referansının write lock altında ATOMİK değişmesidir — salt thread-safe erişim
 * değil. Bu lock, SADECE okuma gerçekten sıksa ve yazma gerçekten nadirse anlamlıdır; aksi halde (yazma da
 * sıksa) writer'lar sürekli reader'ları bloklar ve kazanç kaybolur — o durumda basit bir
 * {@code synchronized} + ConcurrentHashMap bile yeterli olabilirdi.
 */
@Component
public class PricingRulesReadWriteLockCache {

    private static final Logger log = LoggerFactory.getLogger(PricingRulesReadWriteLockCache.class);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final long readDelayMs;
    private final long reloadDelayMs;
    private final AtomicInteger currentReaders = new AtomicInteger();
    private final AtomicInteger maxObservedReaders = new AtomicInteger();
    private final AtomicLong totalReads = new AtomicLong();
    private final AtomicLong totalReloads = new AtomicLong();

    private Map<String, BigDecimal> prices = seedPrices();
    private int version = 0;

    public PricingRulesReadWriteLockCache(ConcurrencyLabProperties properties) {
        this.readDelayMs = properties.pricingCache().readDelayMs();
        this.reloadDelayMs = properties.pricingCache().reloadDelayMs();
    }

    public PriceReadResult read(String productId) {
        lock.readLock().lock();
        int now = currentReaders.incrementAndGet();
        maxObservedReaders.getAndAccumulate(now, Math::max);
        try {
            // LAB ONLY: gerçek bir cache okuması neredeyse anlık olur; bu bekleme sadece birden fazla
            // reader'ın GERÇEKTEN aynı anda içeride olduğunu (currentReaders > 1) gözlemlenebilir yapar.
            sleep(readDelayMs);
            BigDecimal price = prices.get(productId);
            if (price == null) {
                throw new IllegalArgumentException("Unknown productId: " + productId);
            }
            totalReads.incrementAndGet();
            return new PriceReadResult(productId, price, version, now);
        } finally {
            currentReaders.decrementAndGet();
            lock.readLock().unlock();
        }
    }

    public ReloadResult reload() {
        log.info("Pricing reload requested, acquiring write lock (exclusive)...");
        lock.writeLock().lock();
        try {
            // DİKKAT (production riski): write lock tutulurken burada GERÇEK bir external I/O (örn. bir
            // config servisinden rule çekmek) yapılsaydı, bu süre boyunca TÜM reader'lar da bloklanırdı.
            // Bu lab'da bekleme sadece "reload maliyetini" simüle eder; gerçek kodda write lock altındaki
            // iş mümkün olduğunca kısa tutulmalı, I/O lock dışında hazırlanıp sadece referans değişimi
            // lock altında yapılmalıdır.
            sleep(reloadDelayMs);
            version++;
            prices = bumpPrices(prices);
            totalReloads.incrementAndGet();
            log.info("Pricing reload complete: version={}", version);
            return new ReloadResult(version, prices.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public PricingCacheStats stats() {
        return new PricingCacheStats(version, currentReaders.get(), maxObservedReaders.get(), totalReads.get(),
                totalReloads.get());
    }

    public void reset() {
        lock.writeLock().lock();
        try {
            prices = seedPrices();
            version = 0;
            totalReads.set(0);
            totalReloads.set(0);
            maxObservedReaders.set(currentReaders.get());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Map<String, BigDecimal> bumpPrices(Map<String, BigDecimal> old) {
        // Deterministik güncelleme: her reload fiyatları sabit bir artışla değiştirir (random YOK,
        // lab tekrar üretilebilir olmalı).
        Map<String, BigDecimal> updated = new HashMap<>();
        old.forEach((productId, price) -> updated.put(productId, price.add(new BigDecimal("1.00"))));
        return updated;
    }

    private static Map<String, BigDecimal> seedPrices() {
        Map<String, BigDecimal> prices = new HashMap<>();
        prices.put("P1", new BigDecimal("19.90"));
        prices.put("P2", new BigDecimal("49.50"));
        prices.put("P3", new BigDecimal("9.99"));
        prices.put("P4", new BigDecimal("129.00"));
        prices.put("P5", new BigDecimal("5.25"));
        return prices;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pricing cache operation interrupted", e);
        }
    }
}
