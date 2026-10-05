package com.javalabs.pattern.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditStep {

    private static final Logger log = LoggerFactory.getLogger(AuditStep.class);

    public void record(String orderId, boolean paymentSuccess) {
        log.info("Audit recorded for order {} success={}", orderId, paymentSuccess);
    }
}
