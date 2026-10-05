package com.javalabs.concurrency.locks;

import com.javalabs.concurrency.config.ConcurrencyLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

/**
 * Lab 11 — StampedLock: aynı read-heavy pricing senaryosu, ama bu kez OPTIMISTIC READ öğretilir.
 *
 * <p>ReadWriteLock'tan (Lab 10) temel fark: {@code tryOptimisticRead()} gerçek bir lock ALMAZ — sadece
 * o anki stamp'i okur. Okuma sırasında başka bir thread write lock aldıysa (reload), {@code validate()}
 * bunu yakalar ve GEÇERSİZ döner; bu durumda normal {@code readLock()} ile GÜVENLİ fallback yapılır.
 * Kazanç: write çok NADİR olduğu sürece reader'lar hiç lock almadan (CAS/volatile read kadar ucuz) okur.
 *
 * <p>StampedLock NEDEN HER ZAMAN ReadWriteLock yerine kullanılmamalı?
 * <ul>
 *   <li>REENTRANT DEĞİLDİR: aynı thread aynı lock'u ikinci kez almaya çalışırsa (örn. recursive bir
 *       çağrı) DEADLOCK oluşur. ReentrantReadWriteLock bunu güvenle tolere eder.</li>
 *   <li>Optimistic read sonrası okunan veriler validate() ÖNCESİNDE kullanılamaz/yayılamaz; validate
 *       edilmeden döndürülen bir değer tutarsız olabilir — API'si ReadWriteLock'tan daha dikkatli
 *       kullanılmalıdır (kolayca yanlış kullanılabilir).</li>
 *   <li>Write çok SIK oluyorsa optimistic read sürekli fallback'e düşer; bu durumda sade bir
 *       ReadWriteLock (veya hatta ConcurrentHashMap) daha basit ve en az o kadar hızlı olabilir.</li>
 * </ul>
 * Optimistic read'in anlamlı olduğu yer: okuma ÇOK sık, yazma ÇOK nadir (tam olarak pricing cache gibi).
 */
@Component
public class PricingRulesStampedLockCache {

    private static final Logger log = LoggerFactory.getLogger(PricingRulesStampedLockCache.class);

    private final StampedLock lock = new StampedLock();
    private final long readDelayMs;
    private final long reloadDelayMs;
    private final long demoWindowDelayMs;
    private final AtomicLong optimisticAttempts = new AtomicLong();
    private final AtomicLong optimisticSuccess = new AtomicLong();
    private final AtomicLong optimisticFallback = new AtomicLong();
    private final AtomicLong totalReloads = new AtomicLong();

    private Map<String, BigDecimal> prices = seedPrices();
    private int version = 0;

    public PricingRulesStampedLockCache(ConcurrencyLabProperties properties) {
        this.readDelayMs = properties.pricingCache().readDelayMs();
        this.reloadDelayMs = properties.pricingCache().reloadDelayMs();
        this.demoWindowDelayMs = properties.stampedLockDemo().windowDelayMs();
    }

    public StampedPriceReadResult read(String productId) {
        optimisticAttempts.incrementAndGet();
        // tryOptimisticRead: LOCK ALMAZ. Sadece "şu an yazma yok" varsayımıyla o anki stamp'i döner.
        long stamp = lock.tryOptimisticRead();
        BigDecimal price = prices.get(productId);
        int readVersion = version;
        sleep(readDelayMs);

        // validate: stamp alındığından beri bir WRITE olmadıysa true. Olduysa, okuduğumuz price/version
        // TUTARSIZ olabilir (map referansı veya version yarı güncellenmiş görülebilirdi) -> fallback gerekir.
        if (lock.validate(stamp)) {
            optimisticSuccess.incrementAndGet();
            if (price == null) {
                throw new IllegalArgumentException("Unknown productId: " + productId);
            }
            return new StampedPriceReadResult(productId, price, readVersion, true);
        }
        return fallbackRead(productId);
    }

    private StampedPriceReadResult fallbackRead(String productId) {
        optimisticFallback.incrementAndGet();
        long readStamp = lock.readLock();
        try {
            BigDecimal price = prices.get(productId);
            if (price == null) {
                throw new IllegalArgumentException("Unknown productId: " + productId);
            }
            return new StampedPriceReadResult(productId, price, version, false);
        } finally {
            lock.unlockRead(readStamp);
        }
    }

    public ReloadResult reload() {
        long stamp = lock.writeLock();
        try {
            sleep(reloadDelayMs);
            version++;
            prices = bumpPrices(prices);
            totalReloads.incrementAndGet();
            return new ReloadResult(version, prices.size());
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * LAB ONLY — deterministik fallback demosu.
     *
     * <p>Gerçek hayatta optimistic read'in fallback'e düşmesi RACE'E BAĞLIDIR: concurrent bir write'ın
     * TAM OLARAK optimistic read ile validate() arasına denk gelmesi gerekir. Bu şansa bırakılırsa lab
     * tekrar üretilebilir olmaz (section 40). Bu yüzden burada o pencereyi BİLİNÇLİ OLARAK genişletip
     * (LAB ONLY delay) arada GERÇEK bir reload tetikleyip join ile bitmesini garanti ediyoruz.
     * Fallback'in KENDİSİ gerçektir (StampedLock'un normal validate() mekanizması) — sadece zamanlama
     * şansa bırakılmıyor. Bunu bir coordination primitive'in (CountDownLatch/CyclicBarrier) ABA/StampedLock
     * problemini "çözdüğü" şeklinde yanlış sunmuyoruz; sadece deterministik hale getiriyoruz.
     */
    public StampedLockFallbackDemoResult demoForcedFallback(String productId) {
        optimisticAttempts.incrementAndGet();
        long stamp = lock.tryOptimisticRead();
        BigDecimal optimisticValue = prices.get(productId);

        sleep(demoWindowDelayMs);

        Thread writer = Thread.ofVirtual().name("stamped-demo-writer").start(this::reload);
        joinQuietly(writer);

        boolean stillValid = lock.validate(stamp);
        BigDecimal fallbackValue = null;
        if (stillValid) {
            optimisticSuccess.incrementAndGet();
        } else {
            optimisticFallback.incrementAndGet();
            long readStamp = lock.readLock();
            try {
                fallbackValue = prices.get(productId);
            } finally {
                lock.unlockRead(readStamp);
            }
        }
        return new StampedLockFallbackDemoResult(productId, optimisticValue, stillValid, fallbackValue);
    }

    public StampedLockStats stats() {
        return new StampedLockStats(version, optimisticAttempts.get(), optimisticSuccess.get(),
                optimisticFallback.get(), totalReloads.get());
    }

    public void reset() {
        long stamp = lock.writeLock();
        try {
            prices = seedPrices();
            version = 0;
            optimisticAttempts.set(0);
            optimisticSuccess.set(0);
            optimisticFallback.set(0);
            totalReloads.set(0);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private Map<String, BigDecimal> bumpPrices(Map<String, BigDecimal> old) {
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
            throw new IllegalStateException("Stamped pricing cache operation interrupted", e);
        }
    }

    private void joinQuietly(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stamped lock demo interrupted while waiting for writer", e);
        }
    }
}
