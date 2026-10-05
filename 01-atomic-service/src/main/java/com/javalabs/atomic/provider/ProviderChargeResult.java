package com.javalabs.atomic.provider;

/**
 * @param providerInFlightAtStart bu çağrı provider'a girdiği anda provider'da (bu çağrı dahil) kaç aktif request vardı.
 *                                Provider tarafının gözlemidir; uygulamanın kendi sayacıyla karıştırılmamalıdır.
 */
public record ProviderChargeResult(String paymentId, int providerInFlightAtStart, long providerDelayMs) {
}
