package com.javalabs.pattern.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PaymentAuditListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuditListener.class);
    private final List<String> auditRecords = new CopyOnWriteArrayList<>();

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        String record = "AUDIT orderId=" + event.orderId() + " paymentType=" + event.paymentType()
                + " completedAt=" + event.completedAt();
        log.info("[LISTENER:AUDIT] {}", record);
        auditRecords.add(record);
    }

    public List<String> history() {
        return List.copyOf(auditRecords);
    }
}
