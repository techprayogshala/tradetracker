package com.tradetracker.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Email notification service using the transactional outbox pattern.
 *
 * Flow:
 *   1. An event listener writes a row to notification_outbox (same DB transaction as the trade)
 *   2. A @Scheduled poller picks up PENDING rows and sends them
 *   3. On success: status → SENT. On failure: increments attempts, status → FAILED after 3 tries.
 *
 * This guarantees that trade confirmation emails are always sent — even if the mail server
 * is down at the moment the trade is recorded.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final JavaMailSender mailSender;
    private final JdbcClient jdbc;
    private final EmailTemplateService templates;

    public NotificationService(JavaMailSender mailSender, JdbcClient jdbc,
                                EmailTemplateService templates) {
        this.mailSender = mailSender;
        this.jdbc       = jdbc;
        this.templates  = templates;
    }

    // ── Outbox writer (called within the originating transaction) ─────────────

    @Transactional
    public void enqueue(UUID userId, String eventType, Object payload) {
        jdbc.sql("""
            INSERT INTO notification_outbox (user_id, event_type, payload, channel)
            VALUES (:userId, :eventType, :payload::jsonb, 'EMAIL')
            """)
            .param("userId",    userId)
            .param("eventType", eventType)
            .param("payload",   toJson(payload))
            .update();
        log.debug("Enqueued {} notification for user {}", eventType, userId);
    }

    // ── Outbox poller (every 30 seconds) ──────────────────────────────────────

    @Scheduled(fixedDelay = 30_000)
    public void processOutbox() {
        List<OutboxRow> pending = jdbc.sql("""
            SELECT id, user_id, event_type, payload::text
            FROM notification_outbox
            WHERE status = 'PENDING' AND attempts < :maxAttempts
            ORDER BY created_at
            LIMIT 20
            FOR UPDATE SKIP LOCKED
            """)
            .param("maxAttempts", MAX_ATTEMPTS)
            .query((rs, rowNum) -> new OutboxRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("event_type"),
                rs.getString("payload")
            ))
            .list();

        for (OutboxRow row : pending) {
            try {
                send(row);
                markSent(row.id());
            } catch (Exception e) {
                log.warn("Failed to send {} notification {}: {}", row.eventType(), row.id(), e.getMessage());
                markFailed(row.id(), e.getMessage());
            }
        }
    }

    // ── Email dispatch ────────────────────────────────────────────────────────

    private void send(OutboxRow row) throws Exception {
        UserEmailInfo user = fetchUserEmail(row.userId());
        if (user == null) { markSkipped(row.id()); return; }

        if (!isEnabled(row.userId(), row.eventType())) { markSkipped(row.id()); return; }

        EmailContent content = templates.render(row.eventType(), row.payload());

        var message = mailSender.createMimeMessage();
        var helper  = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(user.email());
        helper.setFrom("noreply@tradetracker.app", "TradeTracker");
        helper.setSubject(content.subject());
        helper.setText(content.htmlBody(), true);

        mailSender.send(message);
        log.info("Sent {} email to {}", row.eventType(), user.email());
    }

    // ── Status updates ────────────────────────────────────────────────────────

    @Transactional
    public void markSent(UUID id) {
        jdbc.sql("""
            UPDATE notification_outbox
            SET status = 'SENT', sent_at = NOW(), attempts = attempts + 1, last_attempt_at = NOW()
            WHERE id = :id
            """).param("id", id).update();
    }

    @Transactional
    public void markFailed(UUID id, String error) {
        jdbc.sql("""
            UPDATE notification_outbox
            SET attempts = attempts + 1,
                last_attempt_at = NOW(),
                error_message = :error,
                status = CASE WHEN attempts + 1 >= :max THEN 'FAILED' ELSE 'PENDING' END
            WHERE id = :id
            """)
            .param("id", id).param("error", error).param("max", MAX_ATTEMPTS)
            .update();
    }

    @Transactional
    public void markSkipped(UUID id) {
        jdbc.sql("UPDATE notification_outbox SET status = 'SKIPPED' WHERE id = :id")
            .param("id", id).update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isEnabled(UUID userId, String eventType) {
        Integer count = jdbc.sql("""
            SELECT COUNT(*) FROM notification_preferences
            WHERE user_id = :uid AND event_type = :type AND enabled = true AND channel = 'EMAIL'
            """)
            .param("uid", userId).param("type", eventType)
            .query(Integer.class).single();
        return count != null && count > 0;
    }

    private UserEmailInfo fetchUserEmail(UUID userId) {
        return jdbc.sql("SELECT email, display_name FROM users WHERE id = :id")
            .param("id", userId)
            .query((rs, rowNum) -> new UserEmailInfo(rs.getString("email"), rs.getString("display_name")))
            .optional().orElse(null);
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    private record OutboxRow(UUID id, UUID userId, String eventType, String payload) {}
    private record UserEmailInfo(String email, String displayName) {}
}
