package com.javalabs.pattern.template;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import com.javalabs.pattern.common.PaymentType;
import com.javalabs.pattern.template.bad.BankAProcessorBad;
import com.javalabs.pattern.template.bad.BankBProcessorBad;
import com.javalabs.pattern.template.good.BankAPaymentProcessor;
import com.javalabs.pattern.template.good.BankBPaymentProcessor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/template")
public class TemplateLabController {

    private final BankAProcessorBad bankAProcessorBad;
    private final BankBProcessorBad bankBProcessorBad;
    private final BankAPaymentProcessor bankAPaymentProcessor;
    private final BankBPaymentProcessor bankBPaymentProcessor;

    public TemplateLabController(BankAProcessorBad bankAProcessorBad, BankBProcessorBad bankBProcessorBad,
                                 BankAPaymentProcessor bankAPaymentProcessor,
                                 BankBPaymentProcessor bankBPaymentProcessor) {
        this.bankAProcessorBad = bankAProcessorBad;
        this.bankBProcessorBad = bankBProcessorBad;
        this.bankAPaymentProcessor = bankAPaymentProcessor;
        this.bankBPaymentProcessor = bankBPaymentProcessor;
    }

    @PostMapping("/bad/{provider}")
    public TemplatePaymentResponse bad(@PathVariable String provider, @RequestBody TemplatePaymentRequest request) {
        PaymentCommand command = toCommand(request);
        PaymentResult result = "BANK_B".equalsIgnoreCase(provider)
                ? bankBProcessorBad.process(command)
                : bankAProcessorBad.process(command);
        return new TemplatePaymentResponse("TEMPLATE_BAD", result.orderId(), result.provider(), result.success(),
                result.message());
    }

    @PostMapping("/good/{provider}")
    public TemplatePaymentResponse good(@PathVariable String provider, @RequestBody TemplatePaymentRequest request) {
        PaymentCommand command = toCommand(request);
        PaymentResult result = "BANK_B".equalsIgnoreCase(provider)
                ? bankBPaymentProcessor.process(command)
                : bankAPaymentProcessor.process(command);
        return new TemplatePaymentResponse("TEMPLATE_GOOD", result.orderId(), result.provider(), result.success(),
                result.message());
    }

    private PaymentCommand toCommand(TemplatePaymentRequest request) {
        return new PaymentCommand(request.orderId(), request.amount(), PaymentType.CREDIT_CARD);
    }
}
