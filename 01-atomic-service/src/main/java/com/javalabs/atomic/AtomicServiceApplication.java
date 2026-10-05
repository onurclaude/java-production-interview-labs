package com.javalabs.atomic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AtomicServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtomicServiceApplication.class, args);
    }
}
