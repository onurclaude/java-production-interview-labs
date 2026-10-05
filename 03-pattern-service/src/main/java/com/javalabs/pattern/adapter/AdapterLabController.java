package com.javalabs.pattern.adapter;

import com.javalabs.pattern.adapter.bad.AdapterBadPaymentService;
import com.javalabs.pattern.adapter.good.AdapterGoodPaymentService;
import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/adapter")
public class AdapterLabController {

    private final AdapterBadPaymentService badService;
    private final AdapterGoodPaymentService goodService;

    public AdapterLabController(AdapterBadPaymentService badService, AdapterGoodPaymentService goodService) {
        this.badService = badService;
        this.goodService = goodService;
    }

    @PostMapping("/bad")
    public AdapterChargeResponse bad(@RequestBody AdapterChargeRequest request) {
        PaymentCommand command = new PaymentCommand(request.orderId(), request.amount(), PaymentType.CREDIT_CARD);
        PaymentResult result = badService.pay(command);
        return new AdapterChargeResponse("ADAPTER_BAD", result.orderId(), result.provider(), result.success(),
                result.message());
    }

    @PostMapping("/good")
    public AdapterChargeResponse good(@RequestBody AdapterChargeRequest request) {
        PaymentCommand command = new PaymentCommand(request.orderId(), request.amount(), PaymentType.CREDIT_CARD);
        PaymentResult result = goodService.charge(command, request.provider());
        return new AdapterChargeResponse("ADAPTER_GOOD", result.orderId(), result.provider(), result.success(),
                result.message());
    }
}
