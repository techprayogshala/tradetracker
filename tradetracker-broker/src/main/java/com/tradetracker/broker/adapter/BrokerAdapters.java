package com.tradetracker.broker.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// =============================================================================
// CommSec adapter (ASX retail broker — Australia's largest)
// =============================================================================

/**
 * CommSec doesn't have a public API — integration relies on screen-scraping
 * their CSV export or parsing trade confirmation PDFs.
 *
 * This adapter handles the CSV export format from CommSec's portfolio view.
 * Full OAuth integration requires a CommSec partner agreement (Phase 3).
 */
@Component("COMMSEC")
class CommSecAdapter implements BrokerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommSecAdapter.class);

    @Override
    public String brokerId() { return "COMMSEC"; }

    @Override
    public List<BrokerTrade> fetchTrades(String accessToken, LocalDate since) {
        // CommSec does not offer a public API — trades are fetched by parsing the
        // exported CSV from their "Transaction History" screen.
        //
        // The accessToken field holds the raw CSV content when using the manual
        // import path (uploaded via SettingsPage → Import PDF/CSV).
        // Full OAuth requires a CommSec partner agreement (enterprise only).
        if (accessToken == null || accessToken.isBlank() || !accessToken.contains(",")) {
            log.info("CommSec: no CSV content provided, nothing to import");
            return List.of();
        }

        List<BrokerTrade> trades = new ArrayList<>();
        String[] lines = accessToken.split("\n");

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("Date")) continue;  // Skip header
            String[] row = line.split(",", -1);
            if (row.length < 4) continue;

            try {
                BrokerTrade trade = parseCsvRow(row);
                if (trade != null && !trade.tradeDate().isBefore(since)) {
                    trades.add(trade);
                }
            } catch (Exception e) {
                log.warn("CommSec: skipping unparseable row: {} — {}", line.substring(0, Math.min(60, line.length())), e.getMessage());
            }
        }

        log.info("CommSec CSV parsed {} trades since {}", trades.size(), since);
        return trades;
    }

    @Override
    public Map<String, BigDecimal> fetchCashBalances(String accessToken) {
        return Map.of();
    }

    /**
     * Parse a CommSec trade confirmation CSV line into a BrokerTrade.
     *
     * Example line:
     *   16/01/2024, B0012345, Bought 100 CBA at $105.50 (Brokerage $9.95), 10559.95,, 45000.05
     */
    public BrokerTrade parseCsvRow(String[] row) {
        // CommSec format: Date | Reference | Details | Debit | Credit | Balance
        try {
            String details  = row[2].trim();
            boolean isBuy   = details.startsWith("Bought");
            String tradeType = isBuy ? "BUY" : "SELL";

            // Parse: "Bought 100 CBA at $105.50 (Brokerage $9.95)"
            String[] parts  = details.split("\\s+");
            BigDecimal qty  = new BigDecimal(parts[1]);
            String ticker   = parts[2];
            BigDecimal price = new BigDecimal(parts[4].replace("$", ""));

            // Parse brokerage from parenthetical
            BigDecimal fees = BigDecimal.ZERO;
            int bIdx = details.indexOf("Brokerage $");
            if (bIdx >= 0) {
                String feePart = details.substring(bIdx + 11);
                feePart = feePart.replaceAll("[^0-9.]", "");
                fees = new BigDecimal(feePart);
            }

            LocalDate date = parseCommSecDate(row[0].trim());

            return new BrokerTrade(
                row[1].trim(), ticker, "ASX", tradeType,
                qty, price, fees, "AUD",
                date, date.plusDays(2),   // ASX T+2 settlement
                String.join(",", row)
            );
        } catch (Exception e) {
            log.warn("Failed to parse CommSec CSV row: {}", e.getMessage());
            return null;
        }
    }

    private LocalDate parseCommSecDate(String s) {
        // CommSec format: DD/MM/YYYY
        String[] parts = s.split("/");
        return LocalDate.of(
            Integer.parseInt(parts[2]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[0])
        );
    }
}

// =============================================================================
// SelfWealth adapter (ASX flat-fee broker)
// =============================================================================

@Component("SELFWEALTH")
class SelfWealthAdapter implements BrokerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SelfWealthAdapter.class);
    private final RestClient restClient;

    SelfWealthAdapter(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.selfwealth.com.au").build();
    }

    @Override
    public String brokerId() { return "SELFWEALTH"; }

    @Override
    public List<BrokerTrade> fetchTrades(String accessToken, LocalDate since) {
        log.info("Fetching SelfWealth trades since {}", since);
        try {
            @SuppressWarnings("unchecked")
            var response = restClient.get()
                .uri("/v2/trades?since={since}", since)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

            if (response == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trades = (List<Map<String, Object>>) response.get("trades");
            if (trades == null) return List.of();

            return trades.stream()
                .map(this::normalise)
                .filter(t -> t != null)
                .toList();
        } catch (Exception e) {
            log.error("SelfWealth API error: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, BigDecimal> fetchCashBalances(String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            var response = restClient.get()
                .uri("/v2/accounts/balance")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

            if (response == null) return Map.of();
            return Map.of("AUD", new BigDecimal(response.getOrDefault("cash", "0").toString()));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BrokerTrade normalise(Map<String, Object> raw) {
        try {
            return new BrokerTrade(
                str(raw, "orderId"),
                str(raw, "symbol"),
                "ASX",
                "BUY".equals(str(raw, "side")) ? "BUY" : "SELL",
                dec(raw, "quantity"),
                dec(raw, "avgPrice"),
                dec(raw, "brokerage"),
                "AUD",
                LocalDate.parse(str(raw, "tradeDate")),
                LocalDate.parse(str(raw, "settlementDate")),
                raw.toString()
            );
        } catch (Exception e) {
            log.warn("Failed to normalise SelfWealth trade: {}", e.getMessage());
            return null;
        }
    }

    private static String str(Map<String, Object> m, String k) {
        return m.getOrDefault(k, "").toString();
    }

    private static BigDecimal dec(Map<String, Object> m, String k) {
        return new BigDecimal(m.getOrDefault(k, "0").toString());
    }
}

// =============================================================================
// Interactive Brokers adapter (global, multi-currency)
// =============================================================================

@Component("IBKR")
class InteractiveBrokersAdapter implements BrokerAdapter {

    private static final Logger log = LoggerFactory.getLogger(InteractiveBrokersAdapter.class);
    private final RestClient restClient;

    InteractiveBrokersAdapter(RestClient.Builder builder) {
        // IBKR Client Portal Web API (self-hosted gateway at localhost:5000)
        this.restClient = builder.baseUrl("https://localhost:5000/v1/api").build();
    }

    @Override
    public String brokerId() { return "IBKR"; }

    @Override
    public List<BrokerTrade> fetchTrades(String accessToken, LocalDate since) {
        log.info("Fetching IBKR trades since {}", since);
        try {
            // IBKR Flex Query or Client Portal API
            @SuppressWarnings("unchecked")
            var response = restClient.get()
                .uri("/iserver/account/trades?days={days}", daysSince(since))
                .header("Cookie", "api=" + accessToken)
                .retrieve()
                .body(List.class);

            if (response == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trades = (List<Map<String, Object>>) response;

            return trades.stream()
                .map(this::normalise)
                .filter(t -> t != null)
                .toList();
        } catch (Exception e) {
            log.error("IBKR API error: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, BigDecimal> fetchCashBalances(String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            var response = restClient.get()
                .uri("/iserver/account/summary")
                .header("Cookie", "api=" + accessToken)
                .retrieve()
                .body(Map.class);

            if (response == null) return Map.of();

            // IBKR returns multi-currency balances
            @SuppressWarnings("unchecked")
            Map<String, Object> balances = (Map<String, Object>) response.getOrDefault("cashbalances", Map.of());
            Map<String, BigDecimal> result = new java.util.HashMap<>();
            balances.forEach((currency, amount) ->
                result.put(currency, new BigDecimal(amount.toString())));
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BrokerTrade normalise(Map<String, Object> raw) {
        try {
            String side = str(raw, "side");   // B = Buy, S = Sell
            return new BrokerTrade(
                str(raw, "orderId"),
                str(raw, "symbol"),
                normaliseExchange(str(raw, "exchange")),
                "B".equals(side) ? "BUY" : "SELL",
                dec(raw, "size"),
                dec(raw, "price"),
                dec(raw, "commission").abs(),
                str(raw, "currency"),
                LocalDate.parse(str(raw, "tradeTime").substring(0, 10)),
                null,
                raw.toString()
            );
        } catch (Exception e) {
            log.warn("Failed to normalise IBKR trade: {}", e.getMessage());
            return null;
        }
    }

    private String normaliseExchange(String ibkrExchange) {
        return switch (ibkrExchange) {
            case "ASX"    -> "ASX";
            case "NASDAQ" -> "NASDAQ";
            case "NYSE"   -> "NYSE";
            case "LSE"    -> "LSE";
            default       -> ibkrExchange;
        };
    }

    private long daysSince(LocalDate since) {
        return java.time.temporal.ChronoUnit.DAYS.between(since, LocalDate.now());
    }

    private static String str(Map<String, Object> m, String k) {
        return m.getOrDefault(k, "").toString();
    }

    private static BigDecimal dec(Map<String, Object> m, String k) {
        return new BigDecimal(m.getOrDefault(k, "0").toString());
    }
}
