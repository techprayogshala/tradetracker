package com.tradetracker.portfolio.event;

import java.util.UUID;

/**
 * Published after a new portfolio is created.
 * The scheduler module listens for this to kick off an initial price sync.
 */
public record PortfolioCreatedEvent(UUID portfolioId, String keycloakSub) {}

