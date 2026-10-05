package com.javalabs.atomic.controller;

import com.javalabs.atomic.dto.PaymentAttemptResponse;
import com.javalabs.atomic.dto.ProviderFailureResponse;
import com.javalabs.atomic.dto.LabStatsResponse;
import com.javalabs.atomic.lab.AtomicLabAdminService;
import com.javalabs.atomic.lab.CasPaymentLab;
import com.javalabs.atomic.lab.CheckThenActPaymentLab;
import com.javalabs.atomic.lab.CounterLeakPaymentLab;
import com.javalabs.atomic.lab.PlainIntPaymentLab;
import com.javalabs.atomic.provider.ProviderFailureException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/labs/atomic")
public class AtomicLabController {

    private final PlainIntPaymentLab plainIntLab;
    private final CheckThenActPaymentLab checkThenActLab;
    private final CasPaymentLab casLab;
    private final CounterLeakPaymentLab leakLab;
    private final AtomicLabAdminService adminService;

    public AtomicLabController(PlainIntPaymentLab plainIntLab, CheckThenActPaymentLab checkThenActLab,
                               CasPaymentLab casLab, CounterLeakPaymentLab leakLab, AtomicLabAdminService adminService) {
        this.plainIntLab = plainIntLab;
        this.checkThenActLab = checkThenActLab;
        this.casLab = casLab;
        this.leakLab = leakLab;
        this.adminService = adminService;
    }

    @PostMapping("/plain-int")
    public ResponseEntity<PaymentAttemptResponse> plainInt(@RequestParam(defaultValue = "false") boolean fail) {
        return toResponse(plainIntLab.pay(fail));
    }

    @PostMapping("/check-then-act")
    public ResponseEntity<PaymentAttemptResponse> checkThenAct(@RequestParam(defaultValue = "false") boolean fail) {
        return toResponse(checkThenActLab.pay(fail));
    }

    @PostMapping("/cas")
    public ResponseEntity<PaymentAttemptResponse> cas(@RequestParam(defaultValue = "false") boolean fail) {
        return toResponse(casLab.pay(fail));
    }

    @PostMapping("/leak/bad")
    public ResponseEntity<PaymentAttemptResponse> leakBad(@RequestParam(defaultValue = "false") boolean fail) {
        return toResponse(leakLab.payBad(fail));
    }

    @PostMapping("/leak/good")
    public ResponseEntity<PaymentAttemptResponse> leakGood(@RequestParam(defaultValue = "false") boolean fail) {
        return toResponse(leakLab.payGood(fail));
    }

    @GetMapping("/stats")
    public LabStatsResponse stats() {
        return adminService.stats();
    }

    /**
     * LAB ONLY — production business endpoint'i değildir.
     * Production'da in-memory bir concurrency sayacını dışarıdan sıfırlamak, uçuştaki request'lerin
     * muhasebesini bozar ve limiti sessizce ihlal ettirir. Burada sadece lab'ları tekrar çalıştırmak için var.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        AtomicLabAdminService.ResetResult result = adminService.reset();
        if (!result.done()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "reset", false,
                    "providerCallsInFlight", result.providerCallsInFlight(),
                    "message", "Provider çağrıları devam ediyor; yük bittikten sonra tekrar deneyin."));
        }
        return ResponseEntity.ok(Map.of(
                "reset", true,
                "message", "Lab sayaçları ve metrics sıfırlandı (LAB ONLY endpoint)."));
    }

    /**
     * Limit dolu -> 503 Service Unavailable + Retry-After.
     * Bu bir client hatası (4xx) değil, sunucunun downstream kapasitesinin anlık dolu olmasıdır (bulkhead full).
     * 429 da tartışılabilir; ancak 429 genelde "bu client çok sık istek atıyor" (rate limit) anlamı taşır.
     */
    private ResponseEntity<PaymentAttemptResponse> toResponse(PaymentAttemptResponse response) {
        if (response.isRejected()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Provider hatası -> 502 Bad Gateway: hata bizim değil, arkadaki sistemin. 500 dönüp bırakmıyoruz;
     * client hatanın nereden geldiğini ve tekrar denenebilir olduğunu anlayabilmeli.
     */
    @ExceptionHandler(ProviderFailureException.class)
    public ResponseEntity<ProviderFailureResponse> handleProviderFailure(ProviderFailureException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ProviderFailureResponse(
                "PROVIDER_FAILED",
                request.getRequestURI(),
                e.getMessage(),
                Thread.currentThread().getName(),
                "Slot'un release edilip edilmediğini GET /api/labs/atomic/stats -> activeRequests ile kontrol edin."));
    }
}
