package com.tradetracker.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradetracker.document.parser.PdfTradeExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

/**
 * Handles the full document lifecycle:
 *   1. Upload PDF to MinIO (S3-compatible)
 *   2. Record metadata in document_uploads
 *   3. Async: extract text, parse trade data, update status
 *   4. Expose parsed data for user review before committing as trades
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final S3Client s3;
    private final JdbcClient jdbc;
    private final PdfTradeExtractor extractor;
    private final ObjectMapper objectMapper;
    private final String bucket;

    public DocumentService(
            S3Client s3,
            JdbcClient jdbc,
            PdfTradeExtractor extractor,
            ObjectMapper objectMapper,
            @Value("${aws.s3.buckets.trade-confirmations}") String bucket) {
        this.s3           = s3;
        this.jdbc         = jdbc;
        this.extractor    = extractor;
        this.objectMapper = objectMapper;
        this.bucket       = bucket;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads PDF to MinIO, records metadata, then triggers async parse.
     * Returns the document ID immediately — parsing happens in background.
     */
    @Transactional
    public UUID upload(UUID userId, UUID portfolioId,
                       String filename, String contentType,
                       byte[] bytes) {

        UUID docId  = UUID.randomUUID();
        String s3Key = "confirmations/%s/%s/%s".formatted(userId, docId, filename);

        // Upload to MinIO
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes)
        );

        // Record in DB
        jdbc.sql("""
            INSERT INTO document_uploads
                (id, user_id, portfolio_id, filename, content_type, file_size_bytes, s3_key, parse_status)
            VALUES
                (:id, :userId, :portfolioId, :filename, :contentType, :size, :s3Key, 'PENDING')
            """)
            .param("id",          docId)
            .param("userId",      userId)
            .param("portfolioId", portfolioId)
            .param("filename",    filename)
            .param("contentType", contentType)
            .param("size",        bytes.length)
            .param("s3Key",       s3Key)
            .update();

        // Trigger async parse
        parseAsync(docId, s3Key);

        log.info("Uploaded document {} ({} bytes) for portfolio {}", docId, bytes.length, portfolioId);
        return docId;
    }

    // ── Async parse ───────────────────────────────────────────────────────────

    @Async
    public void parseAsync(UUID docId, String s3Key) {
        markParsing(docId);
        try {
            InputStream stream = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(s3Key).build()
            ).asInputStream();

            PdfTradeExtractor.ExtractionResult result = extractor.extract(stream);

            if (result.success() && result.trade() != null) {
                String parsedJson = objectMapper.writeValueAsString(result.trade());
                jdbc.sql("""
                    UPDATE document_uploads
                    SET parse_status     = 'PARSED',
                        broker_detected  = :broker,
                        parsed_trades    = :trades::jsonb,
                        parse_confidence = :confidence,
                        updated_at       = NOW()
                    WHERE id = :id
                    """)
                    .param("id",         docId)
                    .param("broker",     result.brokerDetected())
                    .param("trades",     parsedJson)
                    .param("confidence", result.trade().confidence())
                    .update();
                log.info("Document {} parsed successfully (confidence {:.0f}%%)",
                    docId, result.trade().confidence() * 100);
            } else {
                markFailed(docId, result.errorMessage());
            }

        } catch (Exception e) {
            log.error("Async parse failed for document {}: {}", docId, e.getMessage());
            markFailed(docId, e.getMessage());
        }
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private void markParsing(UUID id) {
        jdbc.sql("UPDATE document_uploads SET parse_status = 'PARSING', updated_at = NOW() WHERE id = :id")
            .param("id", id).update();
    }

    private void markFailed(UUID id, String error) {
        jdbc.sql("""
            UPDATE document_uploads
            SET parse_status = 'FAILED', error_message = :error, updated_at = NOW()
            WHERE id = :id
            """)
            .param("id", id).param("error", error).update();
    }
}
