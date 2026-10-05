package com.javalabs.concurrency.aba;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/aba")
public class AbaLabController {

    private final AbaBadRoutingService badService;
    private final AbaStampedRoutingService stampedService;

    public AbaLabController(AbaBadRoutingService badService, AbaStampedRoutingService stampedService) {
        this.badService = badService;
        this.stampedService = stampedService;
    }

    @PostMapping("/bad")
    public AbaBadResult bad() {
        return badService.demonstrate();
    }

    @PostMapping("/stamped")
    public AbaStampedResult stamped() {
        return stampedService.demonstrate();
    }
}
