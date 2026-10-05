package com.javalabs.concurrency.admin;

import com.javalabs.concurrency.aba.AbaBadRoutingService;
import com.javalabs.concurrency.aba.AbaStampedRoutingService;
import com.javalabs.concurrency.cow.FeatureRuleRegistry;
import com.javalabs.concurrency.cow.OrderProcessingListenerRegistry;
import com.javalabs.concurrency.downstream.SemaphoreBulkheadLab;
import com.javalabs.concurrency.locks.PricingRulesReadWriteLockCache;
import com.javalabs.concurrency.locks.PricingRulesStampedLockCache;
import com.javalabs.concurrency.provider.CustomerCheckSimulator;
import com.javalabs.concurrency.provider.FraudProviderSimulator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Lab'lar arası ortak gözlem/reset noktası — bir "God Object" DEĞİLDİR: her lab kendi state'ini kendi
 * component'inde tutar (CustomerCheckSimulator, FraudProviderSimulator, cache'ler, registry'ler...).
 * Bu sınıf sadece onları enjekte edip sonuçlarını BİR ARAYA GETİRİR (aggregation). BoundedThreadPoolLab,
 * Lab 1/2/4 (thread) ve coordination lab'ları (latch/barrier/phaser) burada YOKTUR çünkü onlar kalıcı,
 * reset edilmesi gereken bir sayaç tutmaz — her çağrı kendi sonucunu kendi response'unda tam olarak döner.
 */
@Service
public class ConcurrencyAdminService {

    private static final String STATS_NOTE =
            "Tüm değerler JVM-local'dir. Başka instance'ların (pod/process) sayaçları burada görünmez.";

    private final CustomerCheckSimulator customerCheck;
    private final FraudProviderSimulator fraudProvider;
    private final SemaphoreBulkheadLab semaphoreLab;
    private final PricingRulesReadWriteLockCache readWriteLockCache;
    private final PricingRulesStampedLockCache stampedLockCache;
    private final AbaBadRoutingService abaBadRoutingService;
    private final AbaStampedRoutingService abaStampedRoutingService;
    private final OrderProcessingListenerRegistry listenerRegistry;
    private final FeatureRuleRegistry featureRuleRegistry;
    private final Environment environment;

    public ConcurrencyAdminService(CustomerCheckSimulator customerCheck, FraudProviderSimulator fraudProvider,
                                   SemaphoreBulkheadLab semaphoreLab,
                                   PricingRulesReadWriteLockCache readWriteLockCache,
                                   PricingRulesStampedLockCache stampedLockCache,
                                   AbaBadRoutingService abaBadRoutingService,
                                   AbaStampedRoutingService abaStampedRoutingService,
                                   OrderProcessingListenerRegistry listenerRegistry,
                                   FeatureRuleRegistry featureRuleRegistry, Environment environment) {
        this.customerCheck = customerCheck;
        this.fraudProvider = fraudProvider;
        this.semaphoreLab = semaphoreLab;
        this.readWriteLockCache = readWriteLockCache;
        this.stampedLockCache = stampedLockCache;
        this.abaBadRoutingService = abaBadRoutingService;
        this.abaStampedRoutingService = abaStampedRoutingService;
        this.listenerRegistry = listenerRegistry;
        this.featureRuleRegistry = featureRuleRegistry;
        this.environment = environment;
    }

    public ConcurrencyStatsResponse stats() {
        return new ConcurrencyStatsResponse(
                instanceName(),
                customerCheck.observedMaxConcurrency(),
                fraudProvider.stats(),
                semaphoreLab.availablePermits(),
                readWriteLockCache.stats(),
                stampedLockCache.stats(),
                abaBadRoutingService.stats(),
                abaStampedRoutingService.stats(),
                listenerRegistry.list().size(),
                featureRuleRegistry.list().size(),
                STATS_NOTE);
    }

    /**
     * Fraud provider'da uçuşta çağrı varken reset reddedilir: aksi halde o çağrıların finally blokları
     * sıfırlanmış sayaçları negatife düşürebilir (bkz. 01-atomic-service'teki aynı prensip).
     */
    public ResetResult reset() {
        int inFlight = fraudProvider.stats().currentInFlight();
        if (inFlight > 0) {
            return new ResetResult(false, inFlight);
        }
        customerCheck.resetObservations();
        fraudProvider.resetObservations();
        readWriteLockCache.reset();
        stampedLockCache.reset();
        abaBadRoutingService.reset();
        abaStampedRoutingService.reset();
        listenerRegistry.reset();
        featureRuleRegistry.reset();
        return new ResetResult(true, 0);
    }

    private String instanceName() {
        return environment.getProperty("spring.application.name", "concurrency-service")
                + "@" + environment.getProperty("local.server.port", "?");
    }

    public record ResetResult(boolean done, int providerCallsInFlight) {
    }
}
