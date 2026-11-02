package com.shop;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TShirtShopApplication {

    public static void main(String[] args) {
        // One time zone everywhere: logs, JDBC and API timestamps are all UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TShirtShopApplication.class, args);
    }
}
