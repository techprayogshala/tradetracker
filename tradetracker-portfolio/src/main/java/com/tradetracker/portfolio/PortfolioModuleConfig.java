package com.tradetracker.portfolio;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables @CreatedDate / @LastModifiedDate on all BaseEntity subclasses. */
@Configuration
@EnableJpaAuditing
public class PortfolioModuleConfig {}

