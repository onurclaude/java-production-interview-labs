package com.javalabs.pattern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PatternServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatternServiceApplication.class, args);
    }
}
