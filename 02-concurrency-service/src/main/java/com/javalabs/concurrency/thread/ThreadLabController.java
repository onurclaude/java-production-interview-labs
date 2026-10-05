package com.javalabs.concurrency.thread;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/threads")
public class ThreadLabController {

    private final PlatformThreadLab platformThreadLab;
    private final VirtualThreadLab virtualThreadLab;
    private final CpuBoundLab cpuBoundLab;

    public ThreadLabController(PlatformThreadLab platformThreadLab, VirtualThreadLab virtualThreadLab,
                               CpuBoundLab cpuBoundLab) {
        this.platformThreadLab = platformThreadLab;
        this.virtualThreadLab = virtualThreadLab;
        this.cpuBoundLab = cpuBoundLab;
    }

    @PostMapping("/platform")
    public ThreadWorkloadResponse platform(@RequestBody ThreadWorkloadRequest request) {
        return platformThreadLab.run(request.taskCount(), request.delayMs());
    }

    @PostMapping("/virtual")
    public ThreadWorkloadResponse virtual(@RequestBody ThreadWorkloadRequest request) {
        return virtualThreadLab.run(request.taskCount(), request.delayMs());
    }

    @PostMapping("/cpu-bound")
    public CpuBoundResponse cpuBound(@RequestBody CpuBoundRequest request) {
        return cpuBoundLab.run(request.taskCount(), request.workUnits(), request.mode());
    }
}
