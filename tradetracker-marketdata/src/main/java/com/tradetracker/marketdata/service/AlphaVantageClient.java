package com.tradetracker.marketdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin client for the Alpha Vantage REST API.
 *
 * Free tier: 25 requests/day, 5 requests/minute.
 * The scheduler respects this by batching and sleeping between calls.
 *
 * Docs: https://www.alphavantage.co/documentation/
 */
@Component
public class AlphaVantageClient {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public AlphaVantageClient(
            RestClient.Builder builder,
            @Value("${marketdata.alpha-vantage.base-url}") String baseUrl,
            @Value("${marketdata.alpha-vantage.api-key}")  String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey     = apiKey;
    }

    // ── Daily OHLCV ──────────────────────────────────────────────────────────

    /**
     * Fetches full (20+ year) daily OHLCV history for a ticker.
     * Use {@code outputSize = "compact"} for the last 100 days only.
     */
    public List<DailyPrice> fetchDailyAdjusted(String ticker, String outputSize) {
        log.debug("Fetching daily adjusted prices for {}", ticker);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
            .uri(b -> b.queryParam("function",    "TIME_SERIES_DAILY_ADJUSTED")
                       .queryParam("symbol",      ticker)
                       .queryParam("outputsize",  outputSize)
                       .queryParam("datatype",    "json")
                       .queryParam("apikey",      apiKey)
                       .build())
            .retrieve()
            .body(Map.class);

        if (response == null || response.containsKey("Note") || response.containsKey("Information")) {
            log.warn("Alpha Vantage rate limit or error for {}: {}", ticker,
                response != null ? response.get("Note") : "null response");
            return List.of();
        }

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> timeSeries =
            (Map<String, Map<String, String>>) response.get("Time Series (Daily)");

        if (timeSeries == null) {
            log.warn("No time series data returned for {}", ticker);
            return List.of();
        }

        List<DailyPrice> prices = new ArrayList<>(timeSeries.size());
        timeSeries.forEach((dateStr, values) -> {
            try {
                prices.add(new DailyPrice(
                    ticker,
                    LocalDate.parse(dateStr),
                    new BigDecimal(values.get("1. open")),
                    new BigDecimal(values.get("2. high")),
                    new BigDecimal(values.get("3. low")),
                    new BigDecimal(values.get("4. close")),
                    new BigDecimal(values.get("5. adjusted close")),
                    Long.parseLong(values.get("6. volume"))
                ));
            } catch (Exception e) {
                log.warn("Failed to parse price row for {} on {}: {}", ticker, dateStr, e.getMessage());
            }
        });

        log.info("Fetched {} daily prices for {}", prices.size(), ticker);
        return prices;
    }

    /**
     * Fetches only the last 100 days — fast and cheap on quota.
     * Used for the nightly refresh job.
     */
    public List<DailyPrice> fetchRecentPrices(String ticker) {
        return fetchDailyAdjusted(ticker, "compact");
    }

    // ── Global quote (single current price) ──────────────────────────────────

    public BigDecimal fetchCurrentPrice(String ticker) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
            .uri(b -> b.queryParam("function", "GLOBAL_QUOTE")
                       .queryParam("symbol",   ticker)
                       .queryParam("apikey",   apiKey)
                       .build())
            .retrieve()
            .body(Map.class);

        if (response == null) return BigDecimal.ZERO;

        @SuppressWarnings("unchecked")
        Map<String, String> quote = (Map<String, String>) response.get("Global Quote");
        if (quote == null || quote.isEmpty()) return BigDecimal.ZERO;

        try {
            return new BigDecimal(quote.get("05. price"));
        } catch (Exception e) {
            log.warn("Could not parse current price for {}", ticker);
            return BigDecimal.ZERO;
        }
    }

    // ── FX rates ─────────────────────────────────────────────────────────────

    public BigDecimal fetchFxRate(String fromCurrency, String toCurrency) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
            .uri(b -> b.queryParam("function",     "CURRENCY_EXCHANGE_RATE")
                       .queryParam("from_currency", fromCurrency)
                       .queryParam("to_currency",   toCurrency)
                       .queryParam("apikey",        apiKey)
                       .build())
            .retrieve()
            .body(Map.class);

        if (response == null) return BigDecimal.ONE;

        @SuppressWarnings("unchecked")
        Map<String, String> rateData =
            (Map<String, String>) response.get("Realtime Currency Exchange Rate");
        if (rateData == null) return BigDecimal.ONE;

        try {
            return new BigDecimal(rateData.get("5. Exchange Rate"));
        } catch (Exception e) {
            log.warn("Could not parse FX rate {}/{}", fromCurrency, toCurrency);
            return BigDecimal.ONE;
        }
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    public record DailyPrice(
        String ticker,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        long volume
    ) {}
}
