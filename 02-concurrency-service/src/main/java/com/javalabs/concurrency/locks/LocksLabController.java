package com.javalabs.concurrency.locks;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/locks")
public class LocksLabController {

    private final PricingRulesReadWriteLockCache readWriteLockCache;
    private final PricingRulesStampedLockCache stampedLockCache;

    public LocksLabController(PricingRulesReadWriteLockCache readWriteLockCache,
                              PricingRulesStampedLockCache stampedLockCache) {
        this.readWriteLockCache = readWriteLockCache;
        this.stampedLockCache = stampedLockCache;
    }

    @GetMapping("/read-write/price/{productId}")
    public PriceReadResult readWritePrice(@PathVariable String productId) {
        return readWriteLockCache.read(productId);
    }

    @PostMapping("/read-write/reload")
    public ReloadResult readWriteReload() {
        return readWriteLockCache.reload();
    }

    @GetMapping("/read-write/stats")
    public PricingCacheStats readWriteStats() {
        return readWriteLockCache.stats();
    }

    @GetMapping("/stamped/price/{productId}")
    public StampedPriceReadResult stampedPrice(@PathVariable String productId) {
        return stampedLockCache.read(productId);
    }

    @PostMapping("/stamped/reload")
    public ReloadResult stampedReload() {
        return stampedLockCache.reload();
    }

    @PostMapping("/stamped/demo/fallback/{productId}")
    public StampedLockFallbackDemoResult stampedFallbackDemo(@PathVariable String productId) {
        return stampedLockCache.demoForcedFallback(productId);
    }

    @GetMapping("/stamped/stats")
    public StampedLockStats stampedStats() {
        return stampedLockCache.stats();
    }
}
