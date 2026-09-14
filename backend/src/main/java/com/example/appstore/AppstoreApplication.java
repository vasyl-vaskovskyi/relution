package com.example.appstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AppstoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppstoreApplication.class, args);
    }
}
