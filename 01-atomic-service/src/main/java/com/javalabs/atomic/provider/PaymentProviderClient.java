package com.javalabs.atomic.provider;

import com.javalabs.atomic.config.AtomicLabProperties;
import com.javalabs.atomic.config.AtomicLabProperties.ProviderMode;
import com.javalabs.atomic.metrics.LabMetrics;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lab'ların provider'a giden tek kapısı.
 *
 * <p>LOCAL modda simulator aynı JVM'dedir. REMOTE modda simulator başka bir instance'tadır;
 * bu mod sadece multi-instance lab'ında "3 instance'ın toplamda provider'a kaç request gönderdiğini"
 * tek bir yerden ölçebilmek için vardır. Lab kodu iki modda da birebir aynıdır.
 */
@Component
public class PaymentProviderClient {

    private final ProviderMode mode;
    private final PaymentProviderSimulator localSimulator;
    private final RestClient remoteProvider;
    private final LabMetrics metrics;

    // Bu instance'tan çıkıp henüz dönmemiş provider çağrıları. Sadece reset'in güvenli olup olmadığına karar vermek için.
    private final AtomicInteger callsInFlight = new AtomicInteger();

    public PaymentProviderClient(AtomicLabProperties properties, PaymentProviderSimulator localSimulator, LabMetrics metrics) {
        this.mode = properties.provider().mode();
        this.localSimulator = localSimulator;
        this.metrics = metrics;
        this.remoteProvider = RestClient.builder()
                .baseUrl(properties.provider().remoteBaseUrl().toString())
                .requestFactory(remoteRequestFactory(properties.provider().maxDelay()))
                .build();
    }

    public ProviderChargeResult charge(String paymentId, boolean fail) {
        callsInFlight.incrementAndGet();
        try {
            ProviderChargeResult result = mode == ProviderMode.LOCAL
                    ? localSimulator.charge(paymentId, fail)
                    : chargeRemote(paymentId, fail);
            metrics.recordSuccessfulPayment();
            return result;
        } catch (ProviderFailureException e) {
            metrics.recordFailedPayment();
            throw e;
        } finally {
            callsInFlight.decrementAndGet();
        }
    }

    private ProviderChargeResult chargeRemote(String paymentId, boolean fail) {
        try {
            return remoteProvider.post()
                    .uri(uri -> uri.path("/api/simulated-provider/charge")
                            .queryParam("paymentId", paymentId)
                            .queryParam("fail", fail)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ProviderFailureException("Remote provider returned " + response.getStatusCode() + " for " + paymentId);
                    })
                    .body(ProviderChargeResult.class);
        } catch (RestClientException e) {
            throw new ProviderFailureException("Remote provider unreachable for " + paymentId + ": " + e.getMessage(), e);
        }
    }

    public ProviderStats providerStats() {
        if (mode == ProviderMode.LOCAL) {
            return localSimulator.stats();
        }
        try {
            ProviderStats remote = remoteProvider.get().uri("/api/simulated-provider/stats").retrieve().body(ProviderStats.class);
            return remote == null ? null : remote.withScope("SHARED_REMOTE");
        } catch (RestClientException e) {
            return null;
        }
    }

    /**
     * REMOTE modda ortak provider'ın gözlemleri bilinçli olarak sıfırlanmaz:
     * bir instance'ın reset'i diğer instance'ların ölçümünü silmemeli. Ortak provider kendi
     * /api/simulated-provider/reset endpoint'i ile sıfırlanır.
     */
    public void resetLocalProviderObservations() {
        if (mode == ProviderMode.LOCAL) {
            localSimulator.resetObservations();
        }
    }

    public int callsInFlight() {
        return callsInFlight.get();
    }

    public ProviderMode mode() {
        return mode;
    }

    private static JdkClientHttpRequestFactory remoteRequestFactory(Duration providerMaxDelay) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // Timeout provider'ın en uzun simüle gecikmesinden büyük olmalı; aksi halde başarılı çağrılar timeout'a düşer.
        factory.setReadTimeout(providerMaxDelay.plusSeconds(5));
        return factory;
    }
}
