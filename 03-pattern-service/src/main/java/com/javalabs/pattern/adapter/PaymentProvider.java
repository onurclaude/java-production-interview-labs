package com.javalabs.pattern.adapter;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;

/**
 * BUSINESS PROBLEM: Bizim sistemimizin ödeme alırken bildiği TEK şekil budur: {@code charge(command)}.
 * Ama gerçekte ödemeyi gerçekleştiren Bank A ve Bank B'nin API'leri birbirinden TAMAMEN FARKLI
 * (bkz. {@link com.javalabs.pattern.adapter.external.BankAPaymentClient} vs
 * {@link com.javalabs.pattern.adapter.external.BankBPaymentClient}). Business kodumuzu HER provider'ın
 * kendi şekline göre yazarsak (bkz. {@code adapter.bad.AdapterBadPaymentService}), provider değiştiğinde
 * veya yeni bir provider eklendiğinde business kodu da değişir.
 *
 * <p>BU INTERFACE NEYİ DEĞİŞTİRİYOR? Bu, ANTI-CORRUPTION LAYER'ın (bkz. README) somut halidir: dış
 * sistemlerin modelinin ({@code BankAResponse}, {@code BankBPayload}, {@code BankBResult}) bizim domain/
 * business kodumuza SIZMASINI engeller. {@code BankAPaymentAdapter} ve {@code BankBPaymentAdapter},
 * kendi provider'larının şeklini bu TEK contract'a çevirir. Business service sadece
 * {@code PaymentProvider.charge()} der; hangi bankanın hangi field adını kullandığını HİÇ bilmez.
 *
 * <p>PRODUCTION'DA DİKKAT: Yarın Bank A kendi SDK'sını değiştirirse (yeni alan, yeni method adı),
 * değişiklik MÜMKÜN OLDUĞUNCA sadece {@code BankAPaymentAdapter}'da kalmalıdır — business service hiç
 * etkilenmemelidir. Bu garantiyi SADECE business kodun asla provider-specific class import ETMEMESİ
 * sağlar (bkz. README Acceptance — Adapter).
 */
public interface PaymentProvider {

    PaymentResult charge(PaymentCommand command);

    String providerName();
}
