package com.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TShirtShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(TShirtShopApplication.class, args);
    }
}
