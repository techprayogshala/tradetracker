package com.tradetracker.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.tradetracker")
@EntityScan(basePackages = "com.tradetracker")
@EnableJpaRepositories(basePackages = "com.tradetracker")
@EnableCaching
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class TradeTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeTrackerApplication.class, args);
    }
}
