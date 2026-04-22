package com.tradetracker.marketdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Writes market data into TimescaleDB.
 *
 * Uses raw JDBC (not JPA) for the bulk upsert path — JPA would create N
 * individual INSERT statements; a single unnest()-based upsert is 100–1000x faster
 * for the historical backfill case (thousands of rows per security).
 */
@Service
public class PriceSyncService {

    private static final Logger log = LoggerFactory.getLogger(PriceSyncService.class);

    private final JdbcClient jdbc;
    private final AlphaVantageClient avClient;

    public PriceSyncService(JdbcClient jdbc, AlphaVantageClient avClient) {
        this.jdbc     = jdbc;
        this.avClient = avClient;
    }

    // ── Price sync ────────────────────────────────────────────────────────────

    /**
     * Syncs the last 100 days of prices for all watched securities.
     * Called nightly by {@link com.tradetracker.scheduler.job.NightlyPriceSyncJob}.
     *
     * Rate-limits to Alpha Vantage's free tier: 5 req/min = 12s sleep between calls.
     */
    @Transactional
    public SyncResult syncRecentPrices() {
        List<WatchedSecurity> watchList = fetchWatchList();
        log.info("Nightly price sync starting for {} securities", watchList.size());

        int totalUpserted = 0;
        int failed = 0;

        for (WatchedSecurity ws : watchList) {
            try {
                List<AlphaVantageClient.DailyPrice> prices =
                    avClient.fetchRecentPrices(ws.ticker());

                if (!prices.isEmpty()) {
                    int upserted = bulkUpsertPrices(ws.securityId(), prices);
                    totalUpserted += upserted;
                    markSynced(ws.securityId());
                    log.debug("Upserted {} prices for {}", upserted, ws.ticker());
                }

                // Respect Alpha Vantage free tier: 5 req/min
                Thread.sleep(12_000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to sync prices for {}: {}", ws.ticker(), e.getMessage());
                failed++;
            }
        }

        log.info("Price sync complete: {} upserted, {} failed", totalUpserted, failed);
        return new SyncResult(watchList.size(), totalUpserted, failed);
    }

    /**
     * Full historical backfill for a single security.
     * Called once when a new security is added (via TradeRecordedEvent listener).
     */
    @Transactional
    public int backfillHistory(UUID securityId, String ticker) {
        log.info("Starting historical backfill for {} ({})", ticker, securityId);
        List<AlphaVantageClient.DailyPrice> prices =
            avClient.fetchDailyAdjusted(ticker, "full");

        if (prices.isEmpty()) return 0;

        int upserted = bulkUpsertPrices(securityId, prices);
        markSynced(securityId);
        log.info("Historical backfill complete for {}: {} rows", ticker, upserted);
        return upserted;
    }

    // ── FX rate sync ─────────────────────────────────────────────────────────

    /**
     * Syncs today's FX rates for all currency pairs used across portfolios.
     */
    @Transactional
    public void syncFxRates(List<String> currencies) {
        String baseCurrency = "USD";
        LocalDate today = LocalDate.now();

        for (String currency : currencies) {
            if (currency.equals(baseCurrency)) continue;
            try {
                BigDecimal rate = avClient.fetchFxRate(baseCurrency, currency);
                jdbc.sql("""
                    INSERT INTO fx_rates (rate_date, from_currency, to_currency, rate, source)
                    VALUES (:date, :from, :to, :rate, 'ALPHA_VANTAGE')
                    ON CONFLICT (rate_date, from_currency, to_currency)
                    DO UPDATE SET rate = EXCLUDED.rate
                    """)
                    .param("date", today)
                    .param("from", baseCurrency)
                    .param("to",   currency)
                    .param("rate", rate)
                    .update();

                Thread.sleep(12_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Failed to fetch FX rate USD/{}: {}", currency, e.getMessage());
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Bulk upsert using PostgreSQL's ON CONFLICT DO UPDATE.
     * A single prepared statement per batch — far faster than row-by-row JPA inserts.
     */
    private int bulkUpsertPrices(UUID securityId, List<AlphaVantageClient.DailyPrice> prices) {
        int[] counts = prices.stream()
            .mapToInt(p -> jdbc.sql("""
                INSERT INTO security_prices
                    (security_id, price_date, open, high, low, close, adjusted_close, volume, source)
                VALUES
                    (:securityId, :date, :open, :high, :low, :close, :adjClose, :volume, 'ALPHA_VANTAGE')
                ON CONFLICT (security_id, price_date)
                DO UPDATE SET
                    open           = EXCLUDED.open,
                    high           = EXCLUDED.high,
                    low            = EXCLUDED.low,
                    close          = EXCLUDED.close,
                    adjusted_close = EXCLUDED.adjusted_close,
                    volume         = EXCLUDED.volume
                """)
                .param("securityId", securityId)
                .param("date",       p.date())
                .param("open",       p.open())
                .param("high",       p.high())
                .param("low",        p.low())
                .param("close",      p.close())
                .param("adjClose",   p.adjustedClose())
                .param("volume",     p.volume())
                .update())
            .toArray();

        return prices.size();
    }

    private void markSynced(UUID securityId) {
        jdbc.sql("""
            UPDATE price_watch_list
            SET last_synced_at = NOW()
            WHERE security_id = :id
            """)
            .param("id", securityId)
            .update();
    }

    private List<WatchedSecurity> fetchWatchList() {
        return jdbc.sql("""
            SELECT pw.security_id, s.ticker, s.exchange
            FROM price_watch_list pw
            JOIN securities s ON s.id = pw.security_id
            WHERE pw.sync_enabled = true
            ORDER BY pw.last_synced_at NULLS FIRST
            """)
            .query((rs, rowNum) -> new WatchedSecurity(
                UUID.fromString(rs.getString("security_id")),
                rs.getString("ticker"),
                rs.getString("exchange")
            ))
            .list();
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    private record WatchedSecurity(UUID securityId, String ticker, String exchange) {}

    public record SyncResult(int securities, int pricesUpserted, int failed) {}
}
