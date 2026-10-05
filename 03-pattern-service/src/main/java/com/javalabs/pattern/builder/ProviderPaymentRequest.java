package com.javalabs.pattern.builder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * BUSINESS PROBLEM: Bir provider'a gönderilecek ödeme request'i 9 alan taşıyor; bunların 4'ü ZORUNLU
 * ({@code orderId}, {@code customerId}, {@code amount}, {@code currency}), 5'i OPSİYONEL
 * ({@code description}, {@code callbackUrl}, {@code installment}, {@code merchantReference},
 * {@code metadata}). Bunu klasik bir constructor ile yazsaydık:
 * <pre>
 *   new ProviderPaymentRequest(
 *       orderId, customerId, amount, "TRY",
 *       null, null, 0, merchantRef, null       // bu null'lar NE ANLAMA GELİYOR? Sırası şaşarsa ne olur?
 *   );
 * </pre>
 * Bu "telescoping constructor" problemidir: parametre sayısı arttıkça, çağıran taraf HANGİ pozisyonun
 * HANGİ alana ait olduğunu ezbere bilmek zorunda kalır. İki {@code String} parametresinin (örn.
 * {@code callbackUrl} ve {@code merchantReference}) yerini şaşırmak COMPILE-TIME'da YAKALANMAZ.
 *
 * <p>BU BUILDER NEYİ DEĞİŞTİRİYOR? {@link Builder}, her alanı KENDİ ADIYLA set etmeyi sağlar
 * ({@code .orderId(...).amount(...).installment(3)}) — okuyan kişi her satırda HANGİ alanı doldurduğunu
 * görür, sıra karışmaz. Zorunlu alanlar {@link Builder#build()} içinde kontrol edilir (fail-fast);
 * opsiyonel alanlar için sane default'lar vardır (örn. {@code currency="TRY"}, {@code installment=0}).
 * Sonuç nesne IMMUTABLE'dır: build() sonrası hiçbir alan değiştirilemez.
 *
 * <p>NE ZAMAN GEREKSİZ? 2-3 alanlı basit bir DTO için Builder FAZLA KOD'dur (bir Builder class'ı + fluent
 * method'lar + build() + asıl class). O durumda bir Java {@code record} (tüm alanlar zorunluysa) veya
 * basit bir static factory method ({@code of(...)}) çok daha az kodla aynı okunabilirliği sağlar.
 * Builder'ın kazancı: ÇOK SAYIDA opsiyonel alan + aynı tipten birden fazla parametre + immutable nesne
 * isteği BİRLİKTE var olduğunda ortaya çıkar (bkz. README).
 */
public final class ProviderPaymentRequest {

    private final String orderId;
    private final String customerId;
    private final BigDecimal amount;
    private final String currency;
    private final String description;
    private final String callbackUrl;
    private final int installment;
    private final String merchantReference;
    private final Map<String, String> metadata;

    private ProviderPaymentRequest(Builder builder) {
        this.orderId = builder.orderId;
        this.customerId = builder.customerId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.description = builder.description;
        this.callbackUrl = builder.callbackUrl;
        this.installment = builder.installment;
        this.merchantReference = builder.merchantReference;
        this.metadata = Map.copyOf(builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String orderId() {
        return orderId;
    }

    public String customerId() {
        return customerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String description() {
        return description;
    }

    public String callbackUrl() {
        return callbackUrl;
    }

    public int installment() {
        return installment;
    }

    public String merchantReference() {
        return merchantReference;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    /**
     * Fluent builder: her {@code with...}/setter-benzeri method {@code this}'i döner, böylece
     * {@code .orderId(x).amount(y).installment(3)} şeklinde zincirlenebilir. Zorunlu alan kontrolü
     * SADECE {@link #build()} içinde yapılır — ara adımlarda yarım kalmış bir nesne asla DIŞARI ÇIKMAZ.
     */
    public static final class Builder {
        private String orderId;
        private String customerId;
        private BigDecimal amount;
        private String currency = "TRY";
        private String description;
        private String callbackUrl;
        private int installment = 0;
        private String merchantReference;
        private final Map<String, String> metadata = new HashMap<>();

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder installment(int installment) {
            this.installment = installment;
            return this;
        }

        public Builder merchantReference(String merchantReference) {
            this.merchantReference = merchantReference;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public ProviderPaymentRequest build() {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("orderId is required");
            }
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalStateException("customerId is required");
            }
            if (amount == null) {
                throw new IllegalStateException("amount is required");
            }
            return new ProviderPaymentRequest(this);
        }
    }
}
