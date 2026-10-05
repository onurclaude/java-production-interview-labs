package com.javalabs.concurrency.observability;

/**
 * Bir operasyonun hangi thread üzerinde çalıştığının gözlemlenebilir anlık görüntüsü.
 *
 * <p>Bütün lab response'larında tekrar tekrar aynı üç alanı (ad, id, virtual mi) elle yazmak yerine
 * tek bir yerden üretiliyor. Thread.threadId() Java 19+ ile gelen, getId()'nin deprecated olmayan karşılığıdır.
 */
public record ThreadSnapshot(String name, long id, boolean virtual) {

    public static ThreadSnapshot current() {
        Thread t = Thread.currentThread();
        return new ThreadSnapshot(t.getName(), t.threadId(), t.isVirtual());
    }
}
