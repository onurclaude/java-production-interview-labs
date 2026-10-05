package com.javalabs.concurrency.coordination;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/coordination")
public class CoordinationLabController {

    private final CountDownLatchLab countDownLatchLab;
    private final CyclicBarrierLab cyclicBarrierLab;
    private final PhaserLab phaserLab;

    public CoordinationLabController(CountDownLatchLab countDownLatchLab, CyclicBarrierLab cyclicBarrierLab,
                                     PhaserLab phaserLab) {
        this.countDownLatchLab = countDownLatchLab;
        this.cyclicBarrierLab = cyclicBarrierLab;
        this.phaserLab = phaserLab;
    }

    @PostMapping("/latch")
    public CountDownLatchResponse latch(@RequestBody CountDownLatchRequest request) {
        return countDownLatchLab.run(request.customerCheckDelayMs(), request.fraudCheckDelayMs(),
                request.pricingCheckDelayMs(), request.simulateCrashWithoutFinally(), request.awaitTimeoutMs());
    }

    @PostMapping("/barrier")
    public CyclicBarrierResponse barrier(@RequestBody CyclicBarrierRequest request) {
        return cyclicBarrierLab.run(request.workerDelaysMs(), request.rounds());
    }

    @PostMapping("/phaser")
    public PhaserResponse phaser(@RequestBody PhaserRequest request) {
        return phaserLab.run(request.validateDelayMs(), request.enrichDelayMs(), request.finalizeDelayMs());
    }
}
