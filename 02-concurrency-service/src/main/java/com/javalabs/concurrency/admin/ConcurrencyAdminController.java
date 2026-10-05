package com.javalabs.concurrency.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/labs/concurrency")
public class ConcurrencyAdminController {

    private final ConcurrencyAdminService adminService;

    public ConcurrencyAdminController(ConcurrencyAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ConcurrencyStatsResponse stats() {
        return adminService.stats();
    }

    /**
     * LAB ONLY — production business endpoint'i değildir. Lab'ları tekrar tekrar temiz bir state'ten
     * çalıştırabilmek için vardır (bkz. 01-atomic-service'teki aynı prensip).
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        ConcurrencyAdminService.ResetResult result = adminService.reset();
        if (!result.done()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "reset", false,
                    "providerCallsInFlight", result.providerCallsInFlight(),
                    "message", "Fraud provider çağrıları devam ediyor; yük bittikten sonra tekrar deneyin."));
        }
        return ResponseEntity.ok(Map.of(
                "reset", true,
                "message", "Lab sayaçları ve cache'ler sıfırlandı (LAB ONLY endpoint)."));
    }
}
