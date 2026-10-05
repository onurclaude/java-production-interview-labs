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
 * <p>Gerçek senaryo: her order işlendiğinde TÜM registered listener'lar (inventory güncelleme,
 * bildirim gönderme, vb.) sırayla/iterate edilerek çağrılır — bu ÇOK SIK olur. Yeni bir listener
 * eklemek/kaldırmak ise NADİR bir admin/deployment operasyonudur. CopyOnWriteArrayList bu asimetriye
 * göre tasarlanmıştır: okuma/iterate etme asla lock gerektirmez ve asla
 * {@link ConcurrentModificationException} fırlatmaz; her write (add/remove) backing array'in TAMAMINI
 * kopyalar.
 *
 * <p>NEDEN write-heavy sistemde KÖTÜ olabilir? Her add()/remove() O(n) bir array kopyalama maliyeti
 * taşır. Liste büyükse (binlerce eleman) ve mutation SIK ise (saniyede yüzlerce kez), bu kopyalama
 * maliyeti ciddi CPU/GC baskısı yaratır. Bu lab'da liste bilinçli olarak KÜÇÜK tutulmuştur (write
 * maliyetini canlı ÖLÇMEK için değil, sadece mekanizmayı göstermek için) — gerçek write-heavy/large-collection
 * senaryosunda CopyOnWriteArrayList YANLIŞ seçim olurdu (bkz. README Production Trade-offs).
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
