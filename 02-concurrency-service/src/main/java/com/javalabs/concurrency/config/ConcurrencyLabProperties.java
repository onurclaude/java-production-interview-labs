package com.javalabs.concurrency.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Bütün lab'ların magic number'larını tek bir yerde toplar.
 *
 * <p>Neden record + @ConfigurationProperties? 01-atomic-service'teki AtomicLabProperties ile aynı yaklaşım:
 * immutable, constructor'da validasyon yapılabilir, Spring Boot 3.x bunu native olarak destekler.
 * Ayrı bir "Config" POJO + @Value alanları yazmaya göre daha az boilerplate ve daha güvenli (null olamaz).
 */
@ConfigurationProperties("concurrency-lab")
public record ConcurrencyLabProperties(
        @DefaultValue Safety safety,
        @DefaultValue CustomerCheck customerCheck,
        @DefaultValue BoundedExecutor boundedExecutor,
        @DefaultValue Provider provider,
        @DefaultValue PricingCache pricingCache,
        @DefaultValue StampedLockDemo stampedLockDemo) {

    public record Safety(
            @DefaultValue("500") int maxTaskCount,
            @DefaultValue("1000") int maxFixedPoolTaskCount,
            @DefaultValue("50") int maxFixedPoolSize,
            @DefaultValue("200") int maxBoundedTaskCount,
            @DefaultValue("5000") int maxVirtualTaskCount,
            @DefaultValue("500") int maxDownstreamRequestCount,
            @DefaultValue("32") int maxCpuBoundTaskCount,
            @DefaultValue("5000000") int maxCpuBoundWorkUnits,
            @DefaultValue("5000") long maxDelayMs) {
    }

    public record CustomerCheck(@DefaultValue("300") long defaultDelayMs) {
    }

    public record BoundedExecutor(
            @DefaultValue("5") int corePoolSize,
            @DefaultValue("10") int maxPoolSize,
            @DefaultValue("10") int queueCapacity) {
    }

    public record Provider(@DefaultValue Fraud fraud) {
        public record Fraud(@DefaultValue("10") int maxConcurrency, @DefaultValue("250") long delayMs) {
        }
    }

    public record PricingCache(@DefaultValue("30") long readDelayMs, @DefaultValue("400") long reloadDelayMs) {
    }

    public record StampedLockDemo(@DefaultValue("80") long windowDelayMs) {
    }
}
