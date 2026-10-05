package com.javalabs.concurrency.coordination;

import java.util.List;

/** workerDelaysMs tam olarak 3 eleman içermelidir (3 sabit worker). rounds: barrier'ın reusable olduğunu göstermek için kaç kez tekrar çalıştırılacağı. */
public record CyclicBarrierRequest(List<Long> workerDelaysMs, int rounds) {
}
