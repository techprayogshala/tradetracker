package com.tradetracker.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.tradetracker")
@EnableCaching
@EnableAsync
@EnableScheduling
public class TradeTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeTrackerApplication.class, args);
    }
}