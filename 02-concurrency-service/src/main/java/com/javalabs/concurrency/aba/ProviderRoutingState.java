package com.javalabs.concurrency.aba;

/**
 * Runtime provider routing kararını temsil eden immutable değer (örn. "fraud trafiği şu an hangi
 * provider'a yönleniyor"). PROVIDER_A/PROVIDER_B BİLİNÇLİ OLARAK static final, paylaşılan instance'lardır:
 * ABA problemi reference equality (==) üzerinden ortaya çıkar — eşit İÇERİKLİ ama FARKLI instance'lar
 * (örn. her seferinde {@code new ProviderRoutingState("Provider-A")}) klasik ABA'yı GÖSTEREMEZ, çünkü
 * AtomicReference.compareAndSet zaten reference equality kullanır ve iki farklı "A" instance'ı hiç eşit
 * sayılmaz. Gerçek production ABA senaryosu (örn. bir connection/route objesinin cache'ten tekrar
 * kullanılması) tam olarak bu şekilde "aynı instance'a geri dönme" ile ortaya çıkar.
 */
public record ProviderRoutingState(String providerName) {

    public static final ProviderRoutingState PROVIDER_A = new ProviderRoutingState("Provider-A");
    public static final ProviderRoutingState PROVIDER_B = new ProviderRoutingState("Provider-B");
}
