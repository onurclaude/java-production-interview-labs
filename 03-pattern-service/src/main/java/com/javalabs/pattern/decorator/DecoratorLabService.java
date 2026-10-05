package com.javalabs.pattern.decorator;

import com.javalabs.pattern.adapter.PaymentProvider;
import com.javalabs.pattern.adapter.good.BankAPaymentAdapter;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.springframework.stereotype.Service;

/**
 * DEKORATÖR ZİNCİRİ NEREDE KURULUR? Strategy/Adapter lab'larında Spring, bean'leri OTOMATİK olarak
 * resolver'a enjekte ediyordu. Decorator'larda genelde durum FARKLIDIR: zincirin HANGİ SIRADA
 * sarılacağı (Logging dışta, Metrics içte — ya da tam tersi) açık bir TASARIM KARARIDIR, bu yüzden
 * burada BİLİNÇLİ OLARAK elle (constructor'da) kuruyoruz — Spring'in otomatik bean grafiğine
 * bırakmıyoruz. Gerçek projede bu, bir {@code @Bean} factory method'u içinde de yapılabilir.
 *
 * <p>Zincir: {@code LoggingDecorator -> MetricsDecorator -> BankAPaymentAdapter}. Çağrı sırası dıştan
 * içe: önce log "START" yazılır, sonra metrics ölçümü başlar, sonra GERÇEK adapter çalışır, sonra
 * metrics süreyi kapatır, sonra log "END" yazılır.
 */
@Service
public class DecoratorLabService {

    private final PaymentProvider decoratedProvider;
    private final MetricsPaymentProviderDecorator metricsDecorator;

    public DecoratorLabService(BankAPaymentAdapter bankAPaymentAdapter) {
        this.metricsDecorator = new MetricsPaymentProviderDecorator(bankAPaymentAdapter);
        this.decoratedProvider = new LoggingPaymentProviderDecorator(metricsDecorator);
    }

    public PaymentResult charge(PaymentCommand command) {
        return decoratedProvider.charge(command);
    }

    public long observedCallCount() {
        return metricsDecorator.callCount();
    }

    public long observedTotalMs() {
        return metricsDecorator.totalMs();
    }
}
