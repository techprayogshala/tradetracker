package com.tradetracker.api.broker;

import com.tradetracker.broker.service.BrokerSyncService;
import com.tradetracker.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Broker & Documents")
@SecurityRequirement(name = "keycloak")
public class BrokerController {

    private final DocumentService documentService;
    private final BrokerSyncService brokerSyncService;
    private final JdbcClient jdbc;

    public BrokerController(DocumentService documentService,
                             BrokerSyncService brokerSyncService,
                             JdbcClient jdbc) {
        this.documentService  = documentService;
        this.brokerSyncService = brokerSyncService;
        this.jdbc             = jdbc;
    }

    // ── Document upload ───────────────────────────────────────────────────────

    @PostMapping(value = "/v1/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a trade confirmation PDF — async parse")
    public Map<String, String> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam("portfolioId") UUID portfolioId) throws Exception {

        UUID userId = resolveUserId(jwt.getSubject());
        UUID docId  = documentService.upload(
            userId, portfolioId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getBytes()
        );
        return Map.of(
            "documentId", docId.toString(),
            "status",     "PENDING",
            "message",    "PDF uploaded. Parse will complete in a few seconds."
        );
    }

    @GetMapping("/v1/documents/{documentId}")
    @Operation(summary = "Poll parse status of an uploaded document")
    public Map<String, Object> getDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID documentId) {

        return jdbc.sql("""
            SELECT id, filename, parse_status, broker_detected,
                   parse_confidence, parsed_trades::text AS parsed_trades,
                   error_message, created_at
            FROM document_uploads
            WHERE id = :id AND user_id = (SELECT id FROM users WHERE keycloak_sub = :sub)
            """)
            .param("id",  documentId)
            .param("sub", jwt.getSubject())
            .query((rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("filename", rs.getString("filename"));
                row.put("parseStatus", rs.getString("parse_status"));
                row.put("brokerDetected", rs.getString("broker_detected"));
                row.put("confidence", rs.getObject("parse_confidence"));
                row.put("parsedTrades", rs.getString("parsed_trades"));
                row.put("errorMessage", rs.getString("error_message"));
                return row;
            })
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    // ── Broker connections ────────────────────────────────────────────────────

    @GetMapping("/v1/broker/connections")
    @Operation(summary = "List broker connections for the authenticated user")
    public List<Map<String, Object>> listConnections(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("""
            SELECT bc.id, bc.broker, bc.display_name, bc.status,
                   bc.last_sync_at, bc.last_sync_status
            FROM broker_connections bc
            JOIN users u ON u.id = bc.user_id
            WHERE u.keycloak_sub = :sub
            ORDER BY bc.created_at DESC
            """)
            .param("sub", jwt.getSubject())
            .query((rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("broker", rs.getString("broker"));
                row.put("displayName", rs.getString("display_name"));
                row.put("status", rs.getString("status"));
                row.put("lastSyncAt", rs.getString("last_sync_at"));
                row.put("lastSyncStatus", rs.getString("last_sync_status"));
                return row;
            })
            .list();
    }

    @PostMapping("/v1/broker/connections/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger a manual sync for a specific broker connection")
    public Map<String, String> triggerSync(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID connectionId) {
        // Verify ownership then dispatch async sync
        var conn = fetchConnection(connectionId, jwt.getSubject());
        var result = brokerSyncService.syncConnection(conn);
        return Map.of(
            "imported",  String.valueOf(result.imported()),
            "duplicates",String.valueOf(result.duplicates()),
            "failed",    String.valueOf(result.failed())
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(String keycloakSub) {
        return jdbc.sql("SELECT id FROM users WHERE keycloak_sub = :sub")
            .param("sub", keycloakSub)
            .query((rs, rowNum) -> UUID.fromString(rs.getString("id")))
            .single();
    }

    private BrokerSyncService.BrokerConnection fetchConnection(UUID id, String keycloakSub) {
        return jdbc.sql("""
            SELECT bc.id, bc.user_id, bc.portfolio_id, bc.broker,
                   bc.access_token_enc, bc.last_sync_at
            FROM broker_connections bc
            JOIN users u ON u.id = bc.user_id
            WHERE bc.id = :id AND u.keycloak_sub = :sub
            """)
            .param("id",  id)
            .param("sub", keycloakSub)
            .query((rs, rowNum) -> new BrokerSyncService.BrokerConnection(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                UUID.fromString(rs.getString("portfolio_id")),
                rs.getString("broker"),
                rs.getString("access_token_enc"),
                rs.getTimestamp("last_sync_at") != null
                    ? rs.getTimestamp("last_sync_at").toInstant() : null
            ))
            .single();
    }
}
