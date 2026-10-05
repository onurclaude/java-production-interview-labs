package com.javalabs.pattern.facade;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/labs/facade")
public class FacadeLabController {

    private final CheckoutValidationStep validationStep;
    private final PricingStep pricingStep;
    private final PaymentExecutionStep paymentStep;
    private final NotificationStep notificationStep;
    private final AuditStep auditStep;
    private final CheckoutFacade checkoutFacade;

    public FacadeLabController(CheckoutValidationStep validationStep, PricingStep pricingStep,
                               PaymentExecutionStep paymentStep, NotificationStep notificationStep,
                               AuditStep auditStep, CheckoutFacade checkoutFacade) {
        this.validationStep = validationStep;
        this.pricingStep = pricingStep;
        this.paymentStep = paymentStep;
        this.notificationStep = notificationStep;
        this.auditStep = auditStep;
        this.checkoutFacade = checkoutFacade;
    }

    /**
     * BAD: controller'ın KENDİSİ 5 alt sistemi sırayla çağırıyor. Controller artık checkout'un iç
     * mimarisini (hangi adım var, hangi sırada, hangisinin çıktısı diğerine girdi olur) biliyor —
     * bu bilgi HTTP katmanının işi değildir.
     */
    @PostMapping("/bad")
    public CheckoutResult bad(@RequestBody CheckoutRequest request) {
        validationStep.validate(request);
        BigDecimal finalAmount = pricingStep.calculateFinalAmount(request);
        boolean paymentSuccess = paymentStep.charge(request.orderId(), finalAmount);
        notificationStep.notifyCustomer(request.orderId());
        auditStep.record(request.orderId(), paymentSuccess);

        List<String> executedSteps = List.of("VALIDATION", "PRICING", "PAYMENT", "NOTIFICATION", "AUDIT");
        return new CheckoutResult("FACADE_BAD", request.orderId(), finalAmount, paymentSuccess, executedSteps);
    }

    /** GOOD: controller sadece Facade'a delege ediyor, alt sistemlerin varlığından habersiz. */
    @PostMapping("/good")
    public CheckoutResult good(@RequestBody CheckoutRequest request) {
        return checkoutFacade.checkout(request);
    }
}
