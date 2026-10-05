package com.javalabs.atomic.lab;

import com.javalabs.atomic.metrics.LabMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AtomicInteger + compareAndSet ile JVM-local concurrency limiter (bulkhead).
 *
 * <p>Neden synchronized değil? Kritik bölge sadece "bir sayıyı kontrol edip artırmak". Bunun için lock
 * almak, contention olduğunda thread'leri park ettirir (context switch). CAS ise lock-free'dir: thread
 * hiç bloklanmaz, başarısız olursa hemen tekrar dener. Kısa kritik bölgelerde CAS genelde daha ucuzdur.
 * (Bu lab'ın amacı da CAS davranışını gözlemlemek; synchronized karşılaştırması README'de.)
 *
 * <p>Neden Semaphore değil? Gerçek projede bu problem Semaphore.tryAcquire()/release() ile de modellenebilir;
 * Semaphore içeride benzer bir CAS mantığı kullanır. Burada amacımız CAS'ı çıplak haliyle gözlemlemek olduğu
 * için bilinçli olarak CAS kullanıyoruz. Semaphore 02-concurrency-service'in konusu.
 *
 * <p>AtomicInteger neden multi-pod global limit sağlamıyor? Bu sayaç bu JVM'in heap'inde yaşar.
 * Uygulama 3 pod olarak çalışırsa 3 bağımsız sayaç vardır; her biri kendi içinde doğru şekilde
 * "en fazla 20" der ama provider'a toplamda 60 request gidebilir. CAS'ın garantisi tek bir bellek
 * adresi üzerindedir; farklı makinelerdeki JVM'ler aynı adresi paylaşmaz. Global limit için
 * paylaşılan bir koordinasyon noktası gerekir (bu lab'ın kapsamı dışında).
 */
final class CasSlotLimiter {

    private static final Logger log = LoggerFactory.getLogger(CasSlotLimiter.class);

    private final String name;
    private final int limit;
    private final Duration casRaceWindow;
    private final LabMetrics metrics;
    private final AtomicInteger activeRequests = new AtomicInteger();

    CasSlotLimiter(String name, int limit, Duration casRaceWindow, LabMetrics metrics) {
        this.name = name;
        this.limit = limit;
        this.casRaceWindow = casRaceWindow;
        this.metrics = metrics;
    }

    /**
     * CAS neden problemi çözüyor?
     * compareAndSet(current, current + 1) şunu tek bölünmez adımda yapar:
     * "Değer hâlâ benim kontrol ettiğim 'current' ise current+1 yap; değilse hiçbir şey yapma ve false dön."
     * Böylece kontrol (current < limit) ile yazma arasında başka bir thread değeri değiştirdiyse yazma
     * gerçekleşmez. Kontrolümüz eskimiş bir değere dayanıyorsa asla slot alamayız. Check-then-act'teki
     * "kontrol geçti ama arada değer değişti" durumu burada imkânsızdır.
     *
     * CAS retry neden oluşuyor?
     * get() ile compareAndSet() arasında başka bir thread slot aldıysa (veya bıraktıysa) değer değişmiştir.
     * CAS false döner; bu bir hata değil, "kararını eski veriyle verdin, güncel değerle tekrar karar ver"
     * sinyalidir. Döngü güncel değeri okuyup limiti YENİDEN kontrol eder; limit dolduysa reddeder.
     *
     * Contention maliyeti:
     * Aynı anda N thread aynı sayaç için yarışırsa her turda sadece biri kazanır, diğer N-1'i boşa CPU
     * harcayıp tekrar dener. Lock-free "maliyetsiz" demek değildir: yüksek contention altında CAS döngüsü
     * busy-spin'e dönüşür, CPU tüketir ve cache line thread'ler/çekirdekler arasında sürekli el değiştirir.
     * casRetryCount bu boşa giden işi görünür kılar.
     */
    SlotReservation tryReserve() {
        int retries = 0;
        while (true) {
            int current = activeRequests.get();
            if (current >= limit) {
                return SlotReservation.rejected(current, retries);
            }

            // Bu bekleme production kodu değildir; CAS contention'ını deterministik şekilde görünür hale getirmek için lab amacıyla kullanılmıştır.
            // Doğruluğu bozmaz: pencere ne kadar büyük olursa olsun CAS eskimiş bir 'current' ile yazmaz.
            LabOnlyDelay.widenRaceWindow(casRaceWindow);

            if (activeRequests.compareAndSet(current, current + 1)) {
                return SlotReservation.reserved(current, retries);
            }

            retries++;
            metrics.recordCasRetry();
            if (log.isDebugEnabled()) {
                log.debug("[{}] CAS FAILED: expected {} but value is now {} -> retry #{}",
                        name, current, activeRequests.get(), retries);
            }
        }
    }

    /**
     * Slot sadece gerçekten reserve edildiyse release edilmelidir. Yine de sayaç asla negatife düşmemeli:
     * negatif sayaç "limit + |negatif|" kadar request'e izin vermek demektir, yani sessiz bir limit ihlalidir.
     * Bu yüzden decrementAndGet() yerine sıfırın altına inmeyen bir CAS döngüsü kullanıyoruz ve
     * eşleşmeyen release'i hata olarak logluyoruz (gerçek sistemde bu bir bug sinyalidir, alarm üretmelidir).
     */
    void release() {
        while (true) {
            int current = activeRequests.get();
            if (current == 0) {
                log.error("[{}] release() without matching reservation - counter already 0, ignoring (BUG signal)", name);
                return;
            }
            if (activeRequests.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    int activeRequests() {
        return activeRequests.get();
    }

    int limit() {
        return limit;
    }

    void reset() {
        activeRequests.set(0);
    }
}
