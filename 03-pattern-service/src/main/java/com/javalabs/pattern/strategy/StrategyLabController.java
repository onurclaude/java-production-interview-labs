package com.javalabs.pattern.strategy;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.strategy.bad.BadPaymentService;
import com.javalabs.pattern.strategy.good.GoodPaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/strategy")
public class StrategyLabController {

    private final BadPaymentService badPaymentService;
    private final GoodPaymentService goodPaymentService;

    public StrategyLabController(BadPaymentService badPaymentService, GoodPaymentService goodPaymentService) {
        this.badPaymentService = badPaymentService;
        this.goodPaymentService = goodPaymentService;
    }

    @PostMapping("/bad")
    public StrategyPaymentResponse bad(@RequestBody StrategyPaymentRequest request) {
        PaymentCommand command = new PaymentCommand(request.orderId(), request.amount(), request.paymentType());
        PaymentResult result = badPaymentService.pay(command);
        return new StrategyPaymentResponse("STRATEGY_BAD", result.orderId(), request.paymentType().name(),
                "BadPaymentService (if/else, tüm payment type detaylarını biliyor)", result.provider(),
                result.success(), result.message());
    }

    @PostMapping("/good")
    public StrategyPaymentResponse good(@RequestBody StrategyPaymentRequest request) {
        PaymentCommand command = new PaymentCommand(request.orderId(), request.amount(), request.paymentType());
        PaymentResult result = goodPaymentService.pay(command);
        return new StrategyPaymentResponse("STRATEGY_GOOD", result.orderId(), request.paymentType().name(),
                "Resolver tarafından seçilen Spring-managed PaymentStrategy", result.provider(),
                result.success(), result.message());
    }
}
