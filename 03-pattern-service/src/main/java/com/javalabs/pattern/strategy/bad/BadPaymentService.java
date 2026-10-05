package com.javalabs.pattern.strategy.bad;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;
import com.javalabs.pattern.common.ProviderCallSimulator;
import com.javalabs.pattern.config.PatternLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Lab 1 — BAD: tek bir service, if/else ile üç ödeme yönteminin TÜM detaylarını biliyor.
 *
 * <p>BUSINESS PROBLEM: Checkout, CREDIT_CARD / WALLET / BANK_TRANSFER ödemelerini desteklemeli.
 *
 * <p>BAD YAKLAŞIM — if/else'in KENDİSİ kötü değildir; 3 sabit case için if/else gayet okunabilir bir
 * çözümdür. Problem şu AN ortaya çıkar: her payment type'ın "nasıl validate edilir", "hangi provider
 * çağrılır", "hangi özel business rule uygulanır" bilgisi TEK bir class'ta birikir. Bugün 3 type var,
 * yarın APPLE_PAY gelir — bu class değişir. Sonra GOOGLE_PAY gelir — bu class YİNE değişir. Sonra
 * INSTALLMENT_CARD gelir — YİNE değişir. {@code BadPaymentService}, zamanla "her ödeme yönteminin her
 * detayını bilen" bir class'a dönüşür; bunu değiştiren herkes TÜM ödeme yöntemlerinin davranışını
 * etkileme riskiyle çalışır (bir case'i düzeltirken diğerini bozma riski).
 *
 * <p>Karşılaştırma için: {@code strategy.good.GoodPaymentService} AYNI işi yapar ama hiçbir payment
 * type'ın detayını bilmez — sadece doğru {@link com.javalabs.pattern.strategy.PaymentStrategy}'ye
 * yönlendirir.
 */
@Service
public class BadPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BadPaymentService.class);

    private final ProviderCallSimulator providerCall;
    private final long providerDelayMs;

    public BadPaymentService(ProviderCallSimulator providerCall, PatternLabProperties properties) {
        this.providerCall = providerCall;
        this.providerDelayMs = properties.providerDelayMs();
    }

    public PaymentResult pay(PaymentCommand command) {
        // DİKKAT: Bu method büyümeye müsait. Her yeni payment type, burada yeni bir "else if" ve yeni bir
        // private helper method ister. 3 case'te henüz "kötü" görünmüyor olabilir — asıl problem 6., 7.
        // case geldiğinde, ya da bir case'in validasyonu 10 satıra çıktığında belirginleşir.
        if (command.paymentType() == PaymentType.CREDIT_CARD) {
            return payWithCreditCard(command);
        } else if (command.paymentType() == PaymentType.WALLET) {
            return payWithWallet(command);
        } else if (command.paymentType() == PaymentType.BANK_TRANSFER) {
            return payWithBankTransfer(command);
        }
        throw new IllegalArgumentException("Unsupported payment type: " + command.paymentType());
    }

    private PaymentResult payWithCreditCard(PaymentCommand command) {
        // Card-specific validasyon: BadPaymentService bunu bilmek ZORUNDA kaldı.
        if (command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Card payment amount must be positive");
        }
        log.info("[BAD] CREDIT_CARD validation passed for {}, calling card provider", command.orderId());
        providerCall.call(providerDelayMs);
        return PaymentResult.success(command.orderId(), "CARD_PROVIDER_SIMULATOR",
                "Card charged (BAD: PaymentService knows card-specific validation+provider details)");
    }

    private PaymentResult payWithWallet(PaymentCommand command) {
        // Wallet-specific business rule: BadPaymentService bunu da bilmek ZORUNDA kaldı.
        if (command.amount().compareTo(new BigDecimal("10000")) > 0) {
            throw new IllegalArgumentException("Wallet payments above 10000 are not allowed");
        }
        log.info("[BAD] WALLET balance check passed for {}, debiting wallet", command.orderId());
        providerCall.call(providerDelayMs);
        return PaymentResult.success(command.orderId(), "WALLET_LEDGER_SIMULATOR",
                "Wallet debited (BAD: PaymentService knows wallet-specific balance rule)");
    }

    private PaymentResult payWithBankTransfer(PaymentCommand command) {
        log.info("[BAD] BANK_TRANSFER initiated for {}", command.orderId());
        providerCall.call(providerDelayMs);
        return PaymentResult.success(command.orderId(), "BANK_TRANSFER_SIMULATOR",
                "Transfer initiated (BAD: PaymentService knows bank-transfer-specific flow)");
    }
}
