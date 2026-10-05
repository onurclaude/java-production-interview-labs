package com.javalabs.pattern.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationStep {

    private static final Logger log = LoggerFactory.getLogger(NotificationStep.class);

    public void notifyCustomer(String orderId) {
        log.info("Notification sent for order {}", orderId);
    }
}
