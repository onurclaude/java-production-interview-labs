package com.javalabs.pattern.adapter.external;

/**
 * Bank A'nın GERÇEK (simüle) API'sinin dönüş modeli. Bilerek bizim domain modelimizden FARKLI
 * isimlendirme/şekil kullanır ({@code statusCode} string "OK"/"DECLINED", bizim modelimizde ise
 * {@code boolean success}) — gerçek hayatta her external provider'ın kendi sözleşmesi vardır.
 */
public record BankAResponse(String statusCode, String referenceId, String rawMessage) {
}
