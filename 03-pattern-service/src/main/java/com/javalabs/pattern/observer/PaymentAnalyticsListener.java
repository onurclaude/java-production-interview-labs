package com.javalabs.pattern.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PaymentAnalyticsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentAnalyticsListener.class);
    private final List<String> trackedEvents = new CopyOnWriteArrayList<>();
    private final AtomicLong totalTracked = new AtomicLong();

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        String record = "TRACK payment_completed orderId=" + event.orderId() + " paymentType=" + event.paymentType();
        log.info("[LISTENER:ANALYTICS] {}", record);
        trackedEvents.add(record);
        totalTracked.incrementAndGet();
    }

    public List<String> history() {
        return List.copyOf(trackedEvents);
    }

    public long totalTracked() {
        return totalTracked.get();
    }
}
