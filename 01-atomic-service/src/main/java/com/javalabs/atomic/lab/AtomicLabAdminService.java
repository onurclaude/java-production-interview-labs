package com.javalabs.atomic.lab;

import com.javalabs.atomic.config.AtomicLabProperties;
import com.javalabs.atomic.dto.LabStatsResponse;
import com.javalabs.atomic.metrics.LabMetrics;
import com.javalabs.atomic.provider.PaymentProviderClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Lab gözlem ve reset işlemleri. Business akışın parçası değildir.
 */
@Service
public class AtomicLabAdminService {

    private static final String STATS_NOTE =
            "Tüm değerler JVM-local ve ayrı ayrı okunmuş snapshot'lardır. Başka instance'ların sayaçları burada görünmez.";

    private final PlainIntPaymentLab plainIntLab;
    private final CheckThenActPaymentLab checkThenActLab;
    private final CasPaymentLab casLab;
    private final CounterLeakPaymentLab leakLab;
    private final LabMetrics metrics;
    private final PaymentProviderClient provider;
    private final Environment environment;
    private final int limit;

    public AtomicLabAdminService(PlainIntPaymentLab plainIntLab, CheckThenActPaymentLab checkThenActLab,
                                 CasPaymentLab casLab, CounterLeakPaymentLab leakLab, LabMetrics metrics,
                                 PaymentProviderClient provider, Environment environment, AtomicLabProperties properties) {
        this.plainIntLab = plainIntLab;
        this.checkThenActLab = checkThenActLab;
        this.casLab = casLab;
        this.leakLab = leakLab;
        this.metrics = metrics;
        this.provider = provider;
        this.environment = environment;
        this.limit = properties.providerConcurrencyLimit();
    }

    public LabStatsResponse stats() {
        var activeRequests = new LabStatsResponse.ActiveRequests(
                plainIntLab.activeRequests(),
                checkThenActLab.activeRequests(),
                casLab.activeRequests(),
                leakLab.badActiveRequests(),
                leakLab.goodActiveRequests());

        return new LabStatsResponse(instanceName(), limit, provider.mode().name(), activeRequests,
                metrics.snapshot(), provider.providerStats(), STATS_NOTE);
    }

    /**
     * Uçuşta provider çağrısı varken reset reddedilir: o çağrıların finally blokları sıfırlanmış sayaçları
     * negatife düşürür ve sonraki lab'ın sonucu bozulur. Reset'i yük bittikten sonra çağırın.
     */
    public ResetResult reset() {
        int inFlight = provider.callsInFlight();
        if (inFlight > 0) {
            return new ResetResult(false, inFlight);
        }
        plainIntLab.reset();
        checkThenActLab.reset();
        casLab.reset();
        leakLab.reset();
        metrics.reset();
        provider.resetLocalProviderObservations();
        return new ResetResult(true, 0);
    }

    private String instanceName() {
        return environment.getProperty("spring.application.name", "atomic-service")
                + "@" + environment.getProperty("local.server.port", "?");
    }

    public record ResetResult(boolean done, int providerCallsInFlight) {
    }
}
