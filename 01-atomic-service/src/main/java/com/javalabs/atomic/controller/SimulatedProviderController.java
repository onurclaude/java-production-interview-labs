package com.javalabs.atomic.controller;

import com.javalabs.atomic.provider.PaymentProviderSimulator;
import com.javalabs.atomic.provider.ProviderChargeResult;
import com.javalabs.atomic.provider.ProviderFailureException;
import com.javalabs.atomic.provider.ProviderStats;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * "External provider" rolü. Sadece multi-instance lab'ında kullanılır: bir instance (ör. 8090) provider
 * rolünü üstlenir, diğer instance'lar (8081/8082/8083) remote-provider profiliyle buraya HTTP çağrısı yapar.
 * Böylece instance'ların TOPLAMDA provider'a kaç concurrent request gönderdiği tek bir yerde ölçülür.
 */
@RestController
@RequestMapping("/api/simulated-provider")
public class SimulatedProviderController {

    private final PaymentProviderSimulator simulator;

    public SimulatedProviderController(PaymentProviderSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/charge")
    public ProviderChargeResult charge(@RequestParam String paymentId, @RequestParam(defaultValue = "false") boolean fail) {
        return simulator.charge(paymentId, fail);
    }

    @GetMapping("/stats")
    public ProviderStats stats() {
        return simulator.stats();
    }

    /** LAB ONLY: ortak provider'ın gözlemlerini sıfırlar. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        simulator.resetObservations();
        return Map.of("reset", true);
    }

    @ExceptionHandler(ProviderFailureException.class)
    public ResponseEntity<Map<String, String>> handleFailure(ProviderFailureException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
