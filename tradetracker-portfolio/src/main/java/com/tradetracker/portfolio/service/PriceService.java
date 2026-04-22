package com.tradetracker.portfolio.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fetches current (latest available) prices from the security_prices table.
 *
 * The marketdata module is responsible for keeping prices fresh via Quartz jobs.
 * This service is purely a read layer.
 */
@Service
public class PriceService {

    private final JdbcClient jdbc;

    public PriceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the most recent adjusted_close for each security.
     * Missing prices return BigDecimal.ZERO — callers should handle gracefully.
     *
     * Uses a TimescaleDB DISTINCT ON query which is very fast on hypertables.
     */
    @Cacheable(value = "prices", key = "#securityIds.hashCode()")
    public Map<UUID, BigDecimal> getCurrentPrices(List<UUID> securityIds) {
        if (securityIds.isEmpty()) return Map.of();

        String sql = """
            SELECT DISTINCT ON (security_id)
                security_id,
                adjusted_close
            FROM security_prices
            WHERE security_id = ANY(:ids::uuid[])
            ORDER BY security_id, price_date DESC
            """;

        return jdbc.sql(sql)
            .param("ids", securityIds.stream()
                .map(UUID::toString)
                .toArray(String[]::new))
            .query((rs, rowNum) -> Map.entry(
                UUID.fromString(rs.getString("security_id")),
                rs.getBigDecimal("adjusted_close")
            ))
            .list()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Single price lookup — used by tax module for unrealised CGT calculation. */
    @Cacheable(value = "prices", key = "#securityId")
    public BigDecimal getCurrentPrice(UUID securityId) {
        return getCurrentPrices(List.of(securityId))
            .getOrDefault(securityId, BigDecimal.ZERO);
    }
}
