package com.tradetracker.broker.service;

import com.tradetracker.broker.adapter.BrokerAdapter;
import com.tradetracker.broker.adapter.BrokerAdapter.BrokerTrade;
import com.tradetracker.portfolio.entity.TradeEvent;
import com.tradetracker.portfolio.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates broker sync for all active connections.
 *
 * Flow per connection:
 *   1. Decrypt OAuth access token
 *   2. Call adapter.fetchTrades() for new trades since last sync
 *   3. Deduplicate against broker_raw_imports by externalRef
 *   4. For each new trade: call PortfolioService.recordTrade()
 *   5. Store raw payload in broker_raw_imports for audit
 *   6. Update last_sync_at on the connection
 */
@Service
public class BrokerSyncService {

    private static final Logger log = LoggerFactory.getLogger(BrokerSyncService.class);

    private final Map<String, BrokerAdapter> adapters;
    private final PortfolioService portfolioService;
    private final JdbcClient jdbc;
    private final TokenEncryptionService tokenService;

    public BrokerSyncService(
            List<BrokerAdapter> adapterList,
            PortfolioService portfolioService,
            JdbcClient jdbc,
            TokenEncryptionService tokenService) {
        // Index adapters by broker ID for O(1) lookup
        this.adapters         = adapterList.stream()
            .collect(Collectors.toMap(BrokerAdapter::brokerId, Function.identity()));
        this.portfolioService = portfolioService;
        this.jdbc             = jdbc;
        this.tokenService     = tokenService;
    }

    // ── Sync all active connections ───────────────────────────────────────────

    /**
     * Called by the nightly scheduler — syncs all active broker connections.
     */
    public SyncSummary syncAll() {
        List<BrokerConnection> connections = fetchActiveConnections();
        log.info("Starting broker sync for {} connections", connections.size());

        int total = 0, imported = 0, skipped = 0, failed = 0;

        for (BrokerConnection conn : connections) {
            try {
                SyncResult result = syncConnection(conn);
                imported += result.imported();
                skipped  += result.duplicates();
                failed   += result.failed();
                total    += result.total();
            } catch (Exception e) {
                log.error("Sync failed for connection {} ({}): {}",
                    conn.id(), conn.broker(), e.getMessage());
                markConnectionError(conn.id(), e.getMessage());
                failed++;
            }
        }

        return new SyncSummary(connections.size(), total, imported, skipped, failed);
    }

    // ── Single connection sync ────────────────────────────────────────────────

    @Transactional
    public SyncResult syncConnection(BrokerConnection conn) {
        BrokerAdapter adapter = adapters.get(conn.broker());
        if (adapter == null) {
            log.warn("No adapter registered for broker: {}", conn.broker());
            return new SyncResult(0, 0, 0, 1);
        }

        String accessToken = tokenService.decrypt(conn.accessTokenEnc());
        LocalDate since    = conn.lastSyncAt() != null
            ? conn.lastSyncAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate().minusDays(3)   // 3-day overlap for safety
            : LocalDate.now().minusYears(5);                  // First sync: 5 years back

        List<BrokerAdapter.BrokerTrade> trades = adapter.fetchTrades(accessToken, since);
        log.info("Fetched {} trades from {} for connection {}", trades.size(), conn.broker(), conn.id());

        String keycloakSub = fetchKeycloakSub(conn.userId());
        int imported = 0, duplicates = 0, failed = 0;

        for (BrokerAdapter.BrokerTrade trade : trades) {
            try {
                if (isDuplicate(conn.id(), trade.externalRef())) {
                    duplicates++;
                    continue;
                }

                // Record the trade via PortfolioService (creates parcel, publishes events)
                portfolioService.recordTrade(keycloakSub, conn.portfolioId(),
                    new PortfolioService.TradeCommand(
                        trade.ticker(), trade.exchange(), trade.tradeType(),
                        trade.quantity(), trade.price(), trade.fees(),
                        trade.currency(), null,
                        trade.tradeDate(), trade.settlementDate(),
                        null, trade.externalRef(), "Imported from " + conn.broker(),
                        TradeEvent.Source.BROKER_API
                    ));

                // Log the raw import
                logRawImport(conn.id(), conn.broker(), trade.rawPayloadJson(), trade.externalRef());
                imported++;

            } catch (Exception e) {
                log.warn("Failed to import trade {} from {}: {}", trade.externalRef(), conn.broker(), e.getMessage());
                logFailedImport(conn.id(), conn.broker(), trade.rawPayloadJson(), e.getMessage());
                failed++;
            }
        }

        updateLastSync(conn.id(), imported > 0 ? "SUCCESS" : "PARTIAL");
        return new SyncResult(trades.size(), imported, duplicates, failed);
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private boolean isDuplicate(UUID connectionId, String externalRef) {
        if (externalRef == null || externalRef.isBlank()) return false;
        Integer count = jdbc.sql("""
            SELECT COUNT(*) FROM broker_raw_imports
            WHERE connection_id = :connId
              AND raw_payload->>'externalRef' = :ref
              AND status != 'ERROR'
            """)
            .param("connId", connectionId)
            .param("ref",    externalRef)
            .query(Integer.class).single();
        return count != null && count > 0;
    }

    private void logRawImport(UUID connId, String broker, String payload, String ref) {
        jdbc.sql("""
            INSERT INTO broker_raw_imports (connection_id, broker, raw_payload, status)
            VALUES (:connId, :broker, :payload::jsonb, 'PROCESSED')
            """)
            .param("connId",  connId)
            .param("broker",  broker)
            .param("payload", "{\"externalRef\":\"" + ref + "\",\"raw\":" + payload + "}")
            .update();
    }

    private void logFailedImport(UUID connId, String broker, String payload, String error) {
        jdbc.sql("""
            INSERT INTO broker_raw_imports (connection_id, broker, raw_payload, status)
            VALUES (:connId, :broker, :payload::jsonb, 'ERROR')
            """)
            .param("connId",  connId)
            .param("broker",  broker)
            .param("payload", "{\"raw\":" + payload + ",\"error\":\"" + error + "\"}")
            .update();
    }

    private void updateLastSync(UUID connId, String status) {
        jdbc.sql("""
            UPDATE broker_connections
            SET last_sync_at = NOW(), last_sync_status = :status, status = 'ACTIVE'
            WHERE id = :id
            """)
            .param("id", connId).param("status", status).update();
    }

    private void markConnectionError(UUID connId, String error) {
        jdbc.sql("""
            UPDATE broker_connections
            SET status = 'ERROR', last_error = :error, updated_at = NOW()
            WHERE id = :id
            """)
            .param("id", connId).param("error", error).update();
    }

    private List<BrokerConnection> fetchActiveConnections() {
        return jdbc.sql("""
            SELECT id, user_id, portfolio_id, broker,
                   access_token_enc, last_sync_at
            FROM broker_connections
            WHERE status = 'ACTIVE'
            ORDER BY last_sync_at NULLS FIRST
            """)
            .query((rs, rowNum) -> new BrokerConnection(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                UUID.fromString(rs.getString("portfolio_id")),
                rs.getString("broker"),
                rs.getString("access_token_enc"),
                rs.getTimestamp("last_sync_at") != null
                    ? rs.getTimestamp("last_sync_at").toInstant()
                    : null
            ))
            .list();
    }

    private String fetchKeycloakSub(UUID userId) {
        return jdbc.sql("SELECT keycloak_sub FROM users WHERE id = :id")
            .param("id", userId)
            .query(String.class).single();
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    public record BrokerConnection(
        UUID id, UUID userId, UUID portfolioId, String broker,
        String accessTokenEnc, java.time.Instant lastSyncAt
    ) {}

    public record SyncResult(int total, int imported, int duplicates, int failed) {}

    public record SyncSummary(
        int connections, int total, int imported, int skipped, int failed
    ) {}
}
