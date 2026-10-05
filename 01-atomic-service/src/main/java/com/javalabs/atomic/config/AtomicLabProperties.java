package com.javalabs.atomic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("atomic-lab")
public record AtomicLabProperties(
        @DefaultValue("20") int providerConcurrencyLimit,
        @DefaultValue("50ms") Duration raceWindow,
        @DefaultValue("5ms") Duration casRaceWindow,
        @DefaultValue Provider provider) {

    public AtomicLabProperties {
        if (providerConcurrencyLimit <= 0) {
            throw new IllegalArgumentException("atomic-lab.provider-concurrency-limit must be > 0");
        }
        if (raceWindow.isNegative() || casRaceWindow.isNegative()) {
            throw new IllegalArgumentException("atomic-lab race windows must not be negative");
        }
    }

    public record Provider(
            @DefaultValue("LOCAL") ProviderMode mode,
            @DefaultValue("500ms") Duration minDelay,
            @DefaultValue("1000ms") Duration maxDelay,
            @DefaultValue("http://localhost:8090") URI remoteBaseUrl) {

        public Provider {
            if (minDelay.isNegative() || maxDelay.compareTo(minDelay) < 0) {
                throw new IllegalArgumentException("atomic-lab.provider delays must satisfy 0 <= min-delay <= max-delay");
            }
        }
    }

    public enum ProviderMode {
        LOCAL,
        REMOTE
    }
}
