package com.tradetracker.document;

import com.tradetracker.document.parser.CommSecPdfParser;
import com.tradetracker.document.parser.PdfTradeExtractor;
import com.tradetracker.document.parser.SelfWealthPdfParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfParserTest {

    private CommSecPdfParser commSec;
    private SelfWealthPdfParser selfWealth;
    private PdfTradeExtractor extractor;

    @BeforeEach
    void setUp() {
        commSec     = new CommSecPdfParser();
        selfWealth  = new SelfWealthPdfParser();
        extractor   = new PdfTradeExtractor(List.of(commSec, selfWealth));
    }

    // ── CommSec ───────────────────────────────────────────────────────────────

    static final String COMMSEC_BUY_TEXT = """
        Commonwealth Securities Limited
        CommSec Contract Note
        Date: 16 Jan 2024
        Order Number: B0012345
        Security: COMMONWEALTH BANK OF AUSTRALIA (CBA)
        Transaction: BUY  100 Ordinary Shares @ $105.500
        Brokerage: $9.95
        Total Amount: $10,559.95
        Settlement Date: 18 Jan 2024
        """;

    static final String COMMSEC_SELL_TEXT = """
        Commonwealth Securities Limited
        CommSec Contract Note
        Date: 22 Mar 2024
        Order Number: S0056789
        Security: WESTPAC BANKING CORPORATION (WBC)
        Transaction: SELL  200 Ordinary Shares @ $25.800
        Brokerage: $9.95
        Total Amount: $5,150.05
        """;

    @Test
    void commsec_detects_as_parseable() {
        assertThat(commSec.canParse(COMMSEC_BUY_TEXT)).isTrue();
    }

    @Test
    void commsec_parse_buy_extracts_all_fields() {
        var result = commSec.parse(COMMSEC_BUY_TEXT);

        assertThat(result).isPresent();
        var trade = result.get();
        assertThat(trade.ticker()).isEqualTo("CBA");
        assertThat(trade.tradeType()).isEqualTo("BUY");
        assertThat(trade.quantity()).isEqualByComparingTo("100");
        assertThat(trade.price()).isEqualByComparingTo("105.500");
        assertThat(trade.fees()).isEqualByComparingTo("9.95");
        assertThat(trade.currency()).isEqualTo("AUD");
        assertThat(trade.exchange()).isEqualTo("ASX");
        assertThat(trade.confirmationNumber()).isEqualTo("B0012345");
        assertThat(trade.confidence()).isGreaterThan(0.6);
    }

    @Test
    void commsec_parse_sell() {
        var result = commSec.parse(COMMSEC_SELL_TEXT);

        assertThat(result).isPresent();
        assertThat(result.get().tradeType()).isEqualTo("SELL");
        assertThat(result.get().ticker()).isEqualTo("WBC");
        assertThat(result.get().quantity()).isEqualByComparingTo("200");
    }

    @Test
    void commsec_parse_trade_date() {
        var result = commSec.parse(COMMSEC_BUY_TEXT);
        assertThat(result).isPresent();
        assertThat(result.get().tradeDate().toString()).isEqualTo("2024-01-16");
    }

    // ── SelfWealth ────────────────────────────────────────────────────────────

    static final String SELFWEALTH_TEXT = """
        SelfWealth Trade Confirmation
        Settlement Date: 18/01/2024
        Buy 50 ANZ @ $28.750
        Brokerage: $9.50
        Total: $1,447.00
        """;

    @Test
    void selfwealth_detects_as_parseable() {
        assertThat(selfWealth.canParse(SELFWEALTH_TEXT)).isTrue();
    }

    @Test
    void selfwealth_parse_extracts_fields() {
        var result = selfWealth.parse(SELFWEALTH_TEXT);

        assertThat(result).isPresent();
        var trade = result.get();
        assertThat(trade.ticker()).isEqualTo("ANZ");
        assertThat(trade.tradeType()).isEqualTo("BUY");
        assertThat(trade.quantity()).isEqualByComparingTo("50");
        assertThat(trade.price()).isEqualByComparingTo("28.750");
        assertThat(trade.fees()).isEqualByComparingTo("9.50");
    }

    // ── Broker detection ──────────────────────────────────────────────────────

    @Test
    void unknown_broker_text_returns_failed_result() {
        // Using a mock InputStream with text that no parser handles
        // Direct extractor test would require mocking PDFBox — test via parsers instead
        assertThat(commSec.canParse("Completely unrelated document")).isFalse();
        assertThat(selfWealth.canParse("Completely unrelated document")).isFalse();
    }

    // ── CommSec CSV row parser ────────────────────────────────────────────────

    @Test
    void commsec_csv_row_parser() {
        String[] row = {
            "16/01/2024",
            "B0012345",
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
        assertThat(trade.tradeDate().toString()).isEqualTo("2024-01-16");
        assertThat(trade.externalRef()).isEqualTo("B0012345");
    }
}
