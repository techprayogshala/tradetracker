package com.tradetracker.notification.listener;

import com.tradetracker.notification.service.NotificationService;
import com.tradetracker.portfolio.entity.TradeEvent;
import com.tradetracker.portfolio.event.TradeRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to portfolio domain events and enqueues notifications
 * for the transactional outbox.
 *
 * All methods are @Async so they never delay the originating request.
 * The NotificationService poller handles actual delivery separately.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final JdbcClient jdbc;

    public NotificationEventListener(NotificationService notificationService, JdbcClient jdbc) {
        this.notificationService = notificationService;
        this.jdbc                = jdbc;
    }

    /**
     * Enqueue a trade confirmation email for every BUY or SELL.
     * Dividends and transfers are excluded — too noisy for most users.
     */
    @Async
    @EventListener
    public void onTradeRecorded(TradeRecordedEvent event) {
        if (event.tradeType() == TradeEvent.TradeType.DIVIDEND
                || event.tradeType() == TradeEvent.TradeType.RETURN_OF_CAPITAL) {
            return;
        }

        TradeEmailPayload payload = fetchTradePayload(event.tradeId());
        if (payload == null) {
            log.warn("Could not load trade payload for notification: {}", event.tradeId());
            return;
        }

        UUID userId = fetchUserId(event.portfolioId());
        if (userId == null) return;

        notificationService.enqueue(userId, "TRADE_CONFIRMED", Map.of(
            "tradeId",   payload.tradeId(),
            "ticker",    payload.ticker(),
            "tradeType", payload.tradeType(),
            "quantity",  payload.quantity(),
            "price",     payload.price(),
            "totalCost", payload.totalCost(),
            "tradeDate", payload.tradeDate()
        ));
    }

    // ── CGT discount reminder (called by scheduler) ───────────────────────────

    /**
     * Scans for parcels that will reach 12-month CGT discount eligibility
     * within the next 7 days and enqueues reminder emails.
     *
     * Called by a Quartz job on a weekly schedule.
     */
    public void enqueueCgtDiscountReminders() {
        var upcoming = jdbc.sql("""
            SELECT tp.id, s.ticker, tp.acquisition_date, u.id AS user_id
            FROM tax_parcels tp
            JOIN securities s     ON s.id = tp.security_id
            JOIN portfolios po    ON po.id = tp.portfolio_id
            JOIN users u          ON u.id  = po.user_id
            WHERE tp.is_fully_disposed = false
              AND (tp.acquisition_date + INTERVAL '1 year')
                  BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
            """)
            .query((rs, rowNum) -> Map.of(
                "parcelId",            rs.getString("id"),
                "ticker",              rs.getString("ticker"),
                "discountEligibleDate",rs.getString("acquisition_date"),
                "userId",              rs.getString("user_id")
            ))
            .list();

        for (var row : upcoming) {
            try {
                notificationService.enqueue(
                    UUID.fromString(row.get("userId").toString()),
                    "CGT_REMINDER",
                    row
                );
            } catch (Exception e) {
                log.warn("Failed to enqueue CGT reminder: {}", e.getMessage());
            }
        }
        log.info("Enqueued {} CGT discount reminders", upcoming.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TradeEmailPayload fetchTradePayload(UUID tradeId) {
        return jdbc.sql("""
            SELECT t.id, s.ticker, t.trade_type, t.quantity, t.price,
                   (t.price * t.quantity + t.fees) AS total_cost,
                   t.trade_date
            FROM trade_events t
            JOIN securities s ON s.id = t.security_id
            WHERE t.id = :id
            """)
            .param("id", tradeId)
            .query((rs, rowNum) -> new TradeEmailPayload(
                rs.getString("id"),
                rs.getString("ticker"),
                rs.getString("trade_type"),
                rs.getString("quantity"),
                rs.getString("price"),
                rs.getString("total_cost"),
                rs.getString("trade_date")
            ))
            .optional()
            .orElse(null);
    }

    private UUID fetchUserId(UUID portfolioId) {
        return jdbc.sql("SELECT user_id FROM portfolios WHERE id = :id")
            .param("id", portfolioId)
            .query((rs, rowNum) -> UUID.fromString(rs.getString("user_id")))
            .optional()
            .orElse(null);
    }

    private record TradeEmailPayload(
        String tradeId, String ticker, String tradeType,
        String quantity, String price, String totalCost, String tradeDate
    ) {}
}
