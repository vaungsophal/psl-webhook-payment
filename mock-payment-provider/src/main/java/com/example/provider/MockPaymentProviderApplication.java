package com.example.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockPaymentProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockPaymentProviderApplication.class, args);
    }
}
