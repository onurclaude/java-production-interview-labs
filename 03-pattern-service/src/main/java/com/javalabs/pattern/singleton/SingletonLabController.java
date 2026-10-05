package com.javalabs.pattern.singleton;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/singleton")
public class SingletonLabController {

    private final BadPaymentContextService badService;
    private final GoodPaymentContextService goodService;

    public SingletonLabController(BadPaymentContextService badService, GoodPaymentContextService goodService) {
        this.badService = badService;
        this.goodService = goodService;
    }

    @PostMapping("/bad")
    public SingletonResult bad(@RequestBody SingletonRequest request) {
        return badService.process(request.orderId(), request.amount(), request.holdMs());
    }

    @PostMapping("/good")
    public SingletonResult good(@RequestBody SingletonRequest request) {
        return goodService.process(request.orderId(), request.amount(), request.holdMs());
    }
}
