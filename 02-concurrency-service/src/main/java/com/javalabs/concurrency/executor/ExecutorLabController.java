package com.javalabs.concurrency.executor;

import com.javalabs.concurrency.thread.ThreadWorkloadResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/executor")
public class ExecutorLabController {

    private final FixedThreadPoolLab fixedThreadPoolLab;
    private final BoundedThreadPoolLab boundedThreadPoolLab;

    public ExecutorLabController(FixedThreadPoolLab fixedThreadPoolLab, BoundedThreadPoolLab boundedThreadPoolLab) {
        this.fixedThreadPoolLab = fixedThreadPoolLab;
        this.boundedThreadPoolLab = boundedThreadPoolLab;
    }

    @PostMapping("/fixed")
    public ThreadWorkloadResponse fixed(@RequestBody FixedPoolRequest request) {
        return fixedThreadPoolLab.run(request.taskCount(), request.poolSize(), request.delayMs());
    }

    @PostMapping("/bounded")
    public BoundedExecutorResponse bounded(@RequestBody BoundedExecutorRequest request) {
        return boundedThreadPoolLab.run(request.taskCount(), request.delayMs());
    }
}
