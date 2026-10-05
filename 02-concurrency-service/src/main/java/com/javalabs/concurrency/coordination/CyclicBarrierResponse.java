package com.javalabs.concurrency.coordination;

import java.util.List;

public record CyclicBarrierResponse(
        int rounds,
        List<RoundResult> roundResults,
        boolean allRoundsVerified,
        long elapsedMs,
        String note) {

    public record RoundResult(int round, List<Long> phase1EndMs, List<Long> phase2StartMs, boolean verified) {
    }
}
