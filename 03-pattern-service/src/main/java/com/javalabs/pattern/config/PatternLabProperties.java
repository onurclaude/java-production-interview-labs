package com.javalabs.pattern.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties("pattern-lab")
public record PatternLabProperties(
        @DefaultValue("5000.00") BigDecimal checkoutLimitAmount,
        @DefaultValue("150") long providerDelayMs,
        @DefaultValue SingletonLab singletonLab) {

    public record SingletonLab(@DefaultValue("10000") long maxHoldMs) {
    }
}
