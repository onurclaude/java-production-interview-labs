package com.javalabs.concurrency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConcurrencyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConcurrencyServiceApplication.class, args);
    }
}
