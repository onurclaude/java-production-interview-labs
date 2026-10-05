package com.javalabs.pattern.chain;

/**
 * BUSINESS PROBLEM: Checkout tamamlanmadan önce bağımsız 4 kontrol geçmeli: müşteri durumu, fraud,
 * ödeme limiti, stok. Bunları tek bir orchestrator'da sıralı method çağrısı olarak tutarsak (bkz.
 * {@code chain.bad.BadCheckoutValidationService}), o orchestrator "hangi rule var, hangi sırada, hangisi
 * fail-fast, hangisi hangi koşulda çalışır" bilgisinin TAMAMINI bilmek zorunda kalır. Yeni bir rule
 * (örn. "VIP müşteri limit muafiyeti") eklemek orchestrator'ı değiştirmeyi gerektirir.
 *
 * <p>BU INTERFACE NEYİ DEĞİŞTİRİYOR? Her rule kendi kontrolünü BAĞIMSIZ bir class olarak tanımlar.
 * {@link com.javalabs.pattern.chain.good.CheckoutRuleChain} rule'ların LİSTESİNİ Spring'den alır (her
 * rule {@code @Order} ile kendi sırasını bildirir) ve onları sırayla çalıştırıp İLK BAŞARISIZ olanda
 * durur (fail-fast). Chain, rule'ların İÇİNDE ne kontrol ettiğini bilmez — sadece "sırayla çalıştır,
 * biri FAILED derse dur" mantığını bilir.
 *
 * <p>PRODUCTION'DA DİKKAT: 4 rule sabit kalacaksa ve hiçbiri değişmeyecekse, bu interface + 4
 * implementation + bir chain runner, düz 4 method çağrısına göre OVERENGINEERING olabilir. Chain,
 * rule sayısı ARTTIĞINDA, rule'lar BAĞIMSIZ DEĞİŞTİĞİNDE, ORDERING önemli olduğunda ve yeni rule eklemek
 * SIK olduğunda değer kazanır.
 */
public interface CheckoutRule {

    RuleCheckResult check(CheckoutContext context);

    String ruleName();
}
