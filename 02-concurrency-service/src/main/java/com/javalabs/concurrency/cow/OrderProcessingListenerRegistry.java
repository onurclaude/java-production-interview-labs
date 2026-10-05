package com.javalabs.concurrency.cow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lab 13 — CopyOnWriteArrayList: "Order Processing Listener Registry".
 *
 * <p>PROBLEM: Her order işlendiğinde TÜM registered listener'lar (inventory güncelleme, bildirim
 * gönderme, vb.) sırayla iterate edilip çağrılır — bunu günde on binlerce kez yapıyoruz. Yeni bir
 * listener eklemek/kaldırmak ise ayda birkaç kez olan NADİR bir admin/deployment operasyonudur. Normal
 * bir {@code ArrayList} kullansaydık ne olurdu? Bir thread listeyi iterate ederken başka bir thread
 * ekleme/çıkarma yapsa {@code ConcurrentModificationException} fırlardı — ya da bu listeyi normal bir
 * lock'la korusaydık, SANİYEDE ON BİNLERCE okuma her seferinde (gereksiz yere, çünkü okuma okumayla asla
 * çakışmaz) kilit almak zorunda kalırdı.
 *
 * <p>ÇÖZÜM — "yazarken kopyala": {@code CopyOnWriteArrayList.iterator()} çağrıldığı ANDAKİ backing array
 * üzerinde çalışan bir SNAPSHOT döner. Bu snapshot sabittir; iterasyon sürerken liste değişse bile bu
 * iterator'ı ETKİLEMEZ — ne {@code ConcurrentModificationException} fırlar ne de yeni eklenen elemanı
 * görür. Her {@code add()}/{@code remove()} ise backing array'in TAMAMININ yeni bir kopyasını oluşturur
 * ve referansı atomik olarak değiştirir — okuyanlar bunu asla "yarı güncellenmiş" göremez.
 *
 * <p>DİKKAT — write-heavy sistemde NEDEN KÖTÜ olabilir? Her {@code add()}/{@code remove()} O(n) bir array
 * kopyalama maliyeti taşır. Liste büyükse (binlerce eleman) ve mutation da SIK ise (saniyede yüzlerce kez),
 * bu kopyalama ciddi CPU/GC baskısı yaratır — "write-rare" varsayımı burada BOZULUR. Bu lab'da liste
 * bilinçli olarak KÜÇÜK tutulmuştur (write maliyetini canlı ÖLÇMEK için değil, snapshot mekanizmasını
 * göstermek için); gerçek write-heavy/large-collection senaryosunda CopyOnWriteArrayList YANLIŞ seçim
 * olurdu (bkz. README Production Trade-offs).
 */
@Component
public class OrderProcessingListenerRegistry {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingListenerRegistry.class);

    private final CopyOnWriteArrayList<String> listeners =
            new CopyOnWriteArrayList<>(List.of("InventoryListener", "NotificationListener"));

    public List<String> list() {
        return List.copyOf(listeners);
    }

    public AddListenerResult add(String name) {
        long startNanos = System.nanoTime();
        listeners.add(name);
        long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.debug("Listener added: {} (new size={})", name, listeners.size());
        return new AddListenerResult(name, listeners.size(), tookMs);
    }

    public RemoveListenerResult remove(String name) {
        boolean removed = listeners.remove(name);
        return new RemoveListenerResult(name, removed, listeners.size());
    }

    /**
     * Deterministik snapshot semantics demosu.
     *
     * <p>Iterator, {@code listeners.iterator()} çağrıldığı ANDAKİ backing array üzerinde çalışır —
     * bu bir SNAPSHOT'tır. İterasyon SÜRERKEN gerçekten yeni bir listener ekleniyor (ayrı bir thread,
     * deterministik join ile senkronize); iteratedSnapshot bu yeni elemanı GÖRMEMELİ ve HİÇBİR
     * ConcurrentModificationException fırlamamalıdır. java.util.ArrayList ile aynı şeyi denerseniz
     * (iterate ederken modify) CME alırsınız — fark budur.
     */
    public ConcurrentIterationDemoResult demoConcurrentIteration(String nameToAddDuringIteration) {
        List<String> iteratedSnapshot = new ArrayList<>();
        boolean cmeThrown = false;
        boolean mutationTriggered = false;
        Iterator<String> iterator = listeners.iterator();
        try {
            while (iterator.hasNext()) {
                String current = iterator.next();
                iteratedSnapshot.add(current);
                if (!mutationTriggered) {
                    // İterasyon ortasında GERÇEK bir concurrent write tetikle (ayrı thread + join: şansa bırakmıyoruz).
                    Thread writer = Thread.ofVirtual().name("cow-list-demo-writer").start(
                            () -> listeners.add(nameToAddDuringIteration));
                    joinQuietly(writer);
                    mutationTriggered = true;
                }
            }
        } catch (ConcurrentModificationException e) {
            cmeThrown = true;
        }
        return new ConcurrentIterationDemoResult(iteratedSnapshot, nameToAddDuringIteration, List.copyOf(listeners),
                cmeThrown);
    }

    public void reset() {
        listeners.clear();
        listeners.addAll(List.of("InventoryListener", "NotificationListener"));
    }

    private void joinQuietly(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("COW list demo interrupted while waiting for writer", e);
        }
    }
}
