package com.tradetracker.portfolio.event;

import com.tradetracker.portfolio.entity.TradeEvent;

import java.util.UUID;

/**
 * Published after every trade is recorded.
 * <p>
 * Listeners:
 * - calculation module  → invalidates TWR/MWR cache for the portfolio
 * - notification module → sends trade confirmation email (if enabled)
 * - scheduler module    → queues a price fetch for the traded security
 */
public record TradeRecordedEvent(
        UUID tradeId,
        UUID portfolioId,
        TradeEvent.TradeType tradeType
) {
}
