package com.javalabs.pattern.observer;

import com.javalabs.pattern.common.PaymentCommand;
import com.javalabs.pattern.common.PaymentResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/observer")
public class ObserverLabController {

    private final ObserverPaymentService paymentService;
    private final PaymentNotificationListener notificationListener;
    private final PaymentAuditListener auditListener;
    private final PaymentAnalyticsListener analyticsListener;

    public ObserverLabController(ObserverPaymentService paymentService,
                                 PaymentNotificationListener notificationListener,
                                 PaymentAuditListener auditListener,
                                 PaymentAnalyticsListener analyticsListener) {
        this.paymentService = paymentService;
        this.notificationListener = notificationListener;
        this.auditListener = auditListener;
        this.analyticsListener = analyticsListener;
    }

    @PostMapping("/payment-completed")
    public PaymentResult paymentCompleted(@RequestBody ObserverPaymentRequest request) {
        PaymentCommand command = new PaymentCommand(request.orderId(), request.amount(), request.paymentType());
        return paymentService.completePayment(command);
    }

    /** 3 bağımsız listener'ın event'i GERÇEKTEN aldığını kanıtlar — her biri kendi geçmişini tutar. */
    @GetMapping("/listener-log")
    public ListenerLogResponse listenerLog() {
        return new ListenerLogResponse(notificationListener.history(), auditListener.history(),
                analyticsListener.history(), analyticsListener.totalTracked());
    }
}
