package com.tradetracker.api.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/settings")
@Tag(name = "Settings")
@SecurityRequirement(name = "keycloak")
public class SettingsController {

    private final JdbcClient jdbc;

    public SettingsController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── User profile ──────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Get user profile")
    public Map<String, Object> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("""
            SELECT id, email, display_name, base_currency, tax_country, created_at
            FROM users WHERE keycloak_sub = :sub
            """)
            .param("sub", jwt.getSubject())
            .query((rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("email", rs.getString("email"));
                row.put("displayName", rs.getString("display_name"));
                row.put("baseCurrency", rs.getString("base_currency"));
                row.put("taxCountry", rs.getString("tax_country"));
                return row;
            })
            .optional()
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile preferences")
    public void updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest req) {
        jdbc.sql("""
            UPDATE users
            SET display_name  = COALESCE(:name, display_name),
                base_currency = COALESCE(:currency, base_currency),
                tax_country   = COALESCE(:country, tax_country),
                updated_at    = NOW()
            WHERE keycloak_sub = :sub
            """)
            .param("name",     req.displayName())
            .param("currency", req.baseCurrency())
            .param("country",  req.taxCountry())
            .param("sub",      jwt.getSubject())
            .update();
    }

    // ── Notification preferences ──────────────────────────────────────────────

    @GetMapping("/notifications")
    @Operation(summary = "Get notification preferences for the authenticated user")
    public List<Map<String, Object>> getNotificationPreferences(
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = resolveUserId(jwt.getSubject());
        return jdbc.sql("""
            SELECT channel, event_type, enabled, webhook_url
            FROM notification_preferences
            WHERE user_id = :uid
            ORDER BY event_type
            """)
            .param("uid", userId)
            .query((rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("channel", rs.getString("channel"));
                row.put("eventType", rs.getString("event_type"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("webhookUrl", rs.getString("webhook_url") == null ? "" : rs.getString("webhook_url"));
                return row;
            })
            .list();
    }

    @PutMapping("/notifications/{eventType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Enable or disable a notification type")
    public void setNotificationPreference(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String eventType,
            @RequestParam(defaultValue = "EMAIL") String channel,
            @RequestParam boolean enabled) {

        UUID userId = resolveUserId(jwt.getSubject());
        jdbc.sql("""
            INSERT INTO notification_preferences (user_id, channel, event_type, enabled)
            VALUES (:uid, :channel, :type, :enabled)
            ON CONFLICT (user_id, channel, event_type)
            DO UPDATE SET enabled = EXCLUDED.enabled
            """)
            .param("uid",     userId)
            .param("channel", channel)
            .param("type",    eventType)
            .param("enabled", enabled)
            .update();
    }

    @PutMapping("/notifications/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a webhook URL for trade event notifications")
    public void setWebhook(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SetWebhookRequest req) {

        UUID userId = resolveUserId(jwt.getSubject());
        jdbc.sql("""
            INSERT INTO notification_preferences (user_id, channel, event_type, enabled, webhook_url)
            VALUES (:uid, 'WEBHOOK', :type, true, :url)
            ON CONFLICT (user_id, channel, event_type)
            DO UPDATE SET webhook_url = EXCLUDED.webhook_url, enabled = true
            """)
            .param("uid",  userId)
            .param("type", req.eventType())
            .param("url",  req.webhookUrl())
            .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(String sub) {
        return jdbc.sql("SELECT id FROM users WHERE keycloak_sub = :sub")
            .param("sub", sub)
            .query((rs, rowNum) -> UUID.fromString(rs.getString("id")))
            .single();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record UpdateProfileRequest(
        String displayName,
        @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}") String baseCurrency,
        @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{2}") String taxCountry
    ) {}

    public record SetWebhookRequest(
        @NotBlank String eventType,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "https?://.*") String webhookUrl
    ) {}
}
