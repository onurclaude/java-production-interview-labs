package com.javalabs.concurrency.downstream;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/virtual/downstream")
public class DownstreamLabController {

    private final UncontrolledDownstreamLab uncontrolledLab;
    private final SemaphoreBulkheadLab semaphoreLab;

    public DownstreamLabController(UncontrolledDownstreamLab uncontrolledLab, SemaphoreBulkheadLab semaphoreLab) {
        this.uncontrolledLab = uncontrolledLab;
        this.semaphoreLab = semaphoreLab;
    }

    @PostMapping("/bad")
    public DownstreamResponse bad(@RequestBody DownstreamRequest request) {
        return uncontrolledLab.run(request.requestCount());
    }

    @PostMapping("/good")
    public DownstreamResponse good(@RequestBody DownstreamRequest request) {
        return semaphoreLab.run(request.requestCount(), request.useTimeout(), request.timeoutMs());
    }
}
