package com.tradetracker.marketdata.listener;

import com.tradetracker.marketdata.service.PriceSyncService;
import com.tradetracker.portfolio.entity.TradeEvent;
import com.tradetracker.portfolio.event.TradeRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens for new BUY trades and triggers a historical price backfill
 * for any security that doesn't yet have price data.
 *
 * Runs async so it never blocks the trade-recording request.
 */
@Component
public class TradeEventMarketDataListener {

    private static final Logger log = LoggerFactory.getLogger(TradeEventMarketDataListener.class);

    private final PriceSyncService priceSyncService;
    private final JdbcClient jdbc;

    public TradeEventMarketDataListener(PriceSyncService priceSyncService, JdbcClient jdbc) {
        this.priceSyncService = priceSyncService;
        this.jdbc             = jdbc;
    }

    @Async
    @EventListener
    public void onTradeRecorded(TradeRecordedEvent event) {
        // Only BUY and TRANSFER_IN create parcels and need price data
        if (event.tradeType() != TradeEvent.TradeType.BUY
                && event.tradeType() != TradeEvent.TradeType.TRANSFER_IN) {
            return;
        }

        String ticker = resolveTickerForTrade(event.tradeId());
        if (ticker == null) return;

        UUID securityId = resolveSecurityId(event.tradeId());
        if (securityId == null) return;

        // Add to watch list if not already there
        addToWatchListIfNeeded(securityId);

        boolean hasHistory = hasPriceHistory(event.tradeId());
        if (!hasHistory) {
            log.info("New security detected via trade {} — triggering backfill for {}",
                event.tradeId(), ticker);
            priceSyncService.backfillHistory(securityId, ticker);
        }
    }

    private void addToWatchListIfNeeded(UUID securityId) {
        Integer exists = jdbc.sql("SELECT 1 FROM price_watch_list WHERE security_id = :id")
            .param("id", securityId)
            .query(Integer.class)
            .optional()
            .orElse(null);

        if (exists == null) {
            jdbc.sql("INSERT INTO price_watch_list (security_id, added_at, sync_enabled) VALUES (:id, NOW(), true)")
                .param("id", securityId)
                .update();
            log.info("Added security {} to price watch list", securityId);
        }
    }

    private String resolveTickerForTrade(UUID tradeId) {
        return jdbc.sql("""
            SELECT s.ticker FROM trade_events t
            JOIN securities s ON s.id = t.security_id
            WHERE t.id = :id
            """)
            .param("id", tradeId)
            .query(String.class)
            .optional()
            .orElse(null);
    }

    private UUID resolveSecurityId(UUID tradeId) {
        return jdbc.sql("SELECT security_id FROM trade_events WHERE id = :id")
            .param("id", tradeId)
            .query((rs, rowNum) -> UUID.fromString(rs.getString("security_id")))
            .optional()
            .orElse(null);
    }

    private boolean hasPriceHistory(UUID tradeId) {
        Integer count = jdbc.sql("""
            SELECT COUNT(*) FROM security_prices sp
            JOIN trade_events t ON t.security_id = sp.security_id
            WHERE t.id = :id
            """)
            .param("id", tradeId)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }
}
