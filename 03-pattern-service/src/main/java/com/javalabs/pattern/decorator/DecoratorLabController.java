package com.javalabs.pattern.decorator;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/decorator")
public class DecoratorLabController {

    private final DecoratorLabService decoratorLabService;

    public DecoratorLabController(DecoratorLabService decoratorLabService) {
        this.decoratorLabService = decoratorLabService;
    }

    @PostMapping("/charge")
    public DecoratorChargeResponse charge(@RequestBody DecoratorChargeRequest request) {
        PaymentCommand command = new PaymentCommand(request.orderId(), request.amount(), PaymentType.CREDIT_CARD);
        PaymentResult result = decoratorLabService.charge(command);
        return new DecoratorChargeResponse("DECORATOR", result.orderId(), result.success(),
                decoratorLabService.observedCallCount(), decoratorLabService.observedTotalMs(),
                "LoggingDecorator -> MetricsDecorator -> BankAPaymentAdapter");
    }
}
