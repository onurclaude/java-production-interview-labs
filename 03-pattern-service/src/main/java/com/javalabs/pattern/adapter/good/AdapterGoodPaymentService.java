package com.javalabs.pattern.adapter.good;

import com.javalabs.pattern.adapter.PaymentProvider;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BU SERVICE NEDEN TEMİZ KALIYOR? Bu class'ın import listesine bakın: {@code BankAResponse},
 * {@code BankBPayload}, {@code BankBResult} gibi HİÇBİR external model class'ı YOKTUR — sadece
 * {@link PaymentProvider}. Resolver mantığı burada Strategy lab'ındakiyle (Lab 1) AYNI fikri kullanır:
 * Spring'in enjekte ettiği {@code List<PaymentProvider>}'ı {@code providerName() -> provider} Map'ine
 * çevirip O(1) lookup yapar. Yarın Bank C eklenirse, bu service'te TEK SATIR değişmez.
 */
@Service
public class AdapterGoodPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AdapterGoodPaymentService.class);

    private final Map<String, PaymentProvider> providersByName;

    public AdapterGoodPaymentService(List<PaymentProvider> providers) {
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentProvider::providerName, Function.identity()));
        log.info("AdapterGoodPaymentService initialized with providers: {}", providersByName.keySet());
    }

    public PaymentResult charge(PaymentCommand command, String providerName) {
        PaymentProvider provider = providersByName.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("No PaymentProvider registered for " + providerName);
        }
        log.info("[GOOD] Delegating to adapter {} for order {}", provider.getClass().getSimpleName(),
                command.orderId());
        return provider.charge(command);
    }
}
