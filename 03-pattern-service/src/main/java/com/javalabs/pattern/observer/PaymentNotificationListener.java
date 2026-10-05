package com.javalabs.pattern.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code ObserverPaymentService} bu class'ın VARLIĞINI bilmez — Spring, {@code @EventListener}
 * annotation'ını tarayıp bu method'u {@link PaymentCompletedEvent} publish edildiğinde OTOMATİK çağırır.
 */
@Component
public class PaymentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationListener.class);
    private final List<String> sentNotifications = new CopyOnWriteArrayList<>();

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        String message = "Email sent for order " + event.orderId() + " (amount=" + event.amount() + ")";
        log.info("[LISTENER:NOTIFICATION] {}", message);
        sentNotifications.add(message);
    }

    public List<String> history() {
        return List.copyOf(sentNotifications);
    }
}
