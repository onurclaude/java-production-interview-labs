package com.javalabs.pattern.strategy;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;

/**
 * BUSINESS PROBLEM: Checkout, CREDIT_CARD / WALLET / BANK_TRANSFER ödemelerini destekliyor. Her ödeme
 * yönteminin kendi validasyonu, kendi provider çağrısı ve kendi business kuralları var. Bu davranışları
 * TEK bir service'in if/else zincirinde tutarsak (bkz. {@code strategy.bad.BadPaymentService}), o service
 * her yeni ödeme yöntemiyle (APPLE_PAY, GOOGLE_PAY, INSTALLMENT_CARD...) BÜYÜMEYE devam eder.
 *
 * <p>BU INTERFACE NEYİ DEĞİŞTİRİYOR? Bu interface sadece "bir interface kullanmış olmak" için var
 * DEĞİLDİR. Amacı, {@code PaymentService}'in (çağıran taraf) "ödeme NASIL yapılır" bilgisini TAMAMEN
 * unutmasını sağlamaktır. Çağıran taraf sadece şunu bilir: "elimde bir {@code PaymentStrategy} var, ona
 * {@code pay(command)} diyorum." Kart validasyonu, provider çağrısı, bakiye kontrolü gibi detaylar artık
 * SADECE ilgili implementation'ın (örn. {@code CreditCardPaymentStrategy}) bildiği şeylerdir — çağıran
 * tarafa hiç sızmaz. Bu, Open/Closed Principle'ın pratikteki karşılığıdır: yeni bir ödeme yöntemi
 * eklemek için YENİ bir implementation yazılır, MEVCUT kod (resolver, çağıran service) değişmez.
 *
 * <p>PRODUCTION'DA DİKKAT: Sadece 2-3 sabit, asla değişmeyecek ödeme yöntemi varsa bu interface +
 * birkaç implementation + bir resolver katmanı, 10 satırlık bir if/else'e göre OVERENGINEERING olabilir.
 * Strategy'nin kazancı, davranışlar BÜYÜDÜKÇE ve BAĞIMSIZ DEĞİŞTİKÇE ortaya çıkar (bkz. README "Pattern
 * Kullanmamak Ne Zaman Daha İyi?").
 */
public interface PaymentStrategy {

    PaymentResult pay(PaymentCommand command);

    /** Bu strategy'nin hangi {@link PaymentType} için sorumlu olduğu — resolver'ın registry'yi kurmak için kullandığı bilgi. */
    PaymentType supports();
}
