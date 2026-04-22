package com.tradetracker.broker.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Broker adapter unit tests")
class BrokerAdapterTest {

    private CommSecAdapter commSec;

    @BeforeEach
    void setUp() {
        commSec = new CommSecAdapter();
    }

    // ── CommSec CSV parser ────────────────────────────────────────────────────

    @Nested @DisplayName("CommSec CSV row parser")
    class CommSecCsvTests {

        @Test @DisplayName("Parses a standard BUY row")
        void parse_buy_row() {
            String[] row = {
                "16/01/2024", "B0012345",
                "Bought 100 CBA at $105.50 (Brokerage $9.95)",
                "10559.95", "", "45000.05"
            };
            var trade = commSec.parseCsvRow(row);

            assertThat(trade).isNotNull();
            assertThat(trade.ticker()).isEqualTo("CBA");
            assertThat(trade.tradeType()).isEqualTo("BUY");
            assertThat(trade.quantity()).isEqualByComparingTo("100");
            assertThat(trade.price()).isEqualByComparingTo("105.50");
            assertThat(trade.fees()).isEqualByComparingTo("9.95");
            assertThat(trade.currency()).isEqualTo("AUD");
            assertThat(trade.exchange()).isEqualTo("ASX");
            assertThat(trade.tradeDate()).isEqualTo(LocalDate.of(2024, 1, 16));
            assertThat(trade.settlementDate()).isEqualTo(LocalDate.of(2024, 1, 18)); // T+2
            assertThat(trade.externalRef()).isEqualTo("B0012345");
        }

        @Test @DisplayName("Parses a standard SELL row")
        void parse_sell_row() {
            String[] row = {
                "22/03/2024", "S0056789",
                "Sold 200 WBC at $25.80 (Brokerage $9.95)",
                "", "5150.05", "50000.05"
            };
            var trade = commSec.parseCsvRow(row);

            assertThat(trade).isNotNull();
            assertThat(trade.ticker()).isEqualTo("WBC");
            assertThat(trade.tradeType()).isEqualTo("SELL");
            assertThat(trade.quantity()).isEqualByComparingTo("200");
            assertThat(trade.price()).isEqualByComparingTo("25.80");
        }

        @Test @DisplayName("Parses row with comma-separated quantity (1,000)")
        void parse_large_quantity_with_comma() {
            String[] row = {
                "15/06/2024", "B0099999",
                "Bought 1,000 BHP at $45.20 (Brokerage $19.95)",
                "45219.95", "", "100000.00"
            };
            var trade = commSec.parseCsvRow(row);

            assertThat(trade).isNotNull();
            assertThat(trade.quantity()).isEqualByComparingTo("1000");
        }

        @Test @DisplayName("Parses date in DD/MM/YYYY format correctly")
        void date_parsing_dd_mm_yyyy() {
            String[] row = {
                "01/07/2024", "B0000001",
                "Bought 10 NAB at $32.00 (Brokerage $9.95)",
                "329.95", "", "10000.00"
            };
            var trade = commSec.parseCsvRow(row);
            assertThat(trade.tradeDate()).isEqualTo(LocalDate.of(2024, 7, 1));
        }

        @Test @DisplayName("Returns null for unrecognisable row format")
        void returns_null_for_bad_row() {
            String[] badRow = { "not-a-date", "REF123", "Not a trade description" };
            var trade = commSec.parseCsvRow(badRow);
            assertThat(trade).isNull();
        }

        @Test @DisplayName("fetchTrades returns empty list when no CSV content")
        void fetch_trades_empty_csv() {
            var trades = commSec.fetchTrades("", LocalDate.now().minusYears(1));
            assertThat(trades).isEmpty();
        }

        @Test @DisplayName("fetchTrades parses multi-line CSV string")
        void fetch_trades_multi_line_csv() {
            String csv = """
                Date,Reference,Details,Debit,Credit,Balance
                16/01/2024,B0012345,Bought 100 CBA at $105.50 (Brokerage $9.95),10559.95,,45000.05
                22/03/2024,S0056789,Sold 50 WBC at $28.00 (Brokerage $9.95),,1390.05,46390.10
                """;

            var trades = commSec.fetchTrades(csv, LocalDate.of(2024, 1, 1));
            assertThat(trades).hasSize(2);
            assertThat(trades.get(0).tradeType()).isEqualTo("BUY");
            assertThat(trades.get(1).tradeType()).isEqualTo("SELL");
        }

        @Test @DisplayName("fetchTrades filters by since date")
        void fetch_trades_filters_by_date() {
            String csv = """
                Date,Reference,Details,Debit,Credit,Balance
                16/01/2023,B0011111,Bought 100 ANZ at $25.00 (Brokerage $9.95),2509.95,,40000.00
                16/01/2024,B0012345,Bought 100 CBA at $105.50 (Brokerage $9.95),10559.95,,45000.05
                """;

            // Only fetch since 2024 — should exclude the 2023 trade
            var trades = commSec.fetchTrades(csv, LocalDate.of(2024, 1, 1));
            assertThat(trades).hasSize(1);
            assertThat(trades.getFirst().ticker()).isEqualTo("CBA");
        }

        @Test @DisplayName("brokerId returns COMMSEC")
        void broker_id() {
            assertThat(commSec.brokerId()).isEqualTo("COMMSEC");
        }
    }

    // ── BrokerTrade record ────────────────────────────────────────────────────

    @Nested @DisplayName("BrokerTrade value object")
    class BrokerTradeTests {

        @Test @DisplayName("BrokerTrade fields are accessible")
        void fields_accessible() {
            var trade = new BrokerAdapter.BrokerTrade(
                "REF001", "CBA", "ASX", "BUY",
                new BigDecimal("100"), new BigDecimal("105.50"),
                new BigDecimal("9.95"), "AUD",
                LocalDate.of(2024, 1, 16), LocalDate.of(2024, 1, 18),
                "{\"raw\": \"data\"}"
            );

            assertThat(trade.ticker()).isEqualTo("CBA");
            assertThat(trade.tradeType()).isEqualTo("BUY");
            assertThat(trade.quantity()).isEqualByComparingTo("100");
            assertThat(trade.currency()).isEqualTo("AUD");
        }
    }
}
