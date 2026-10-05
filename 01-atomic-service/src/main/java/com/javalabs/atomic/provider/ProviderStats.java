package com.javalabs.atomic.provider;

/**
 * Provider tarafından yapılan GÖZLEM. Uygulamanın limit mekanizmasının bir parçası değildir;
 * sadece "provider gerçekte aynı anda kaç request gördü?" sorusunu cevaplar.
 *
 * @param scope                 LOCAL_JVM: simulator bu instance içinde. SHARED_REMOTE: birden fazla instance'ın ortak provider'ı.
 * @param capacityViolationCalls provider'a, içerideki aktif request sayısı kapasiteyi aşmışken giren çağrı sayısı.
 */
public record ProviderStats(
        String scope,
        int capacity,
        int currentInFlight,
        int observedMaxConcurrency,
        long totalCalls,
        long capacityViolationCalls,
        boolean capacityRespected) {

    public ProviderStats withScope(String newScope) {
        return new ProviderStats(newScope, capacity, currentInFlight, observedMaxConcurrency,
                totalCalls, capacityViolationCalls, capacityRespected);
    }
}
