package com.javalabs.concurrency.provider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Şu an kaç çağrı aktif" ve "gözlenen maksimum eşzamanlılık" ölçümünü birden fazla simulator'da
 * tekrar yazmamak için çıkarılmış küçük bir yardımcı. Spring bean DEĞİLDİR: her simulator kendi
 * instance'ını field olarak tutar. 01-atomic-service'teki PaymentProviderSimulator'daki
 * inFlight/observedMaxConcurrency çiftinin genel halidir.
 */
public final class ConcurrencyObserver {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger observedMax = new AtomicInteger();

    /** Çağrı başlarken invoke edilir, o anki (bu çağrı dahil) aktif sayıyı döner. */
    public int enter() {
        int now = inFlight.incrementAndGet();
        // getAndAccumulate: "yeni değer büyükse yaz" check-then-act'ini tek CAS döngüsünde atomik yapar.
        // get()+set() yazılsaydı iki thread birbirinin yazdığı maksimumu ezebilirdi.
        observedMax.getAndAccumulate(now, Math::max);
        return now;
    }

    /** Çağrı bittiğinde (başarı/hata farketmez) invoke edilmelidir — çağıran taraf bunu finally'de yapmalı. */
    public void exit() {
        inFlight.decrementAndGet();
    }

    public int current() {
        return inFlight.get();
    }

    public int observedMax() {
        return observedMax.get();
    }

    /** Uçuştaki çağrı yoksa gözlenen maksimumu o anki (genelde 0) değere çeker. */
    public void reset() {
        observedMax.set(inFlight.get());
    }
}
