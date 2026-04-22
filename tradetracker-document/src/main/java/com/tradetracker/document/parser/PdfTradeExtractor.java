package com.tradetracker.document.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// =============================================================================
// TradeConfirmationParser — strategy interface
// =============================================================================

interface TradeConfirmationParser {
    String brokerId();
    boolean canParse(String pdfText);
    Optional<ParsedTrade> parse(String pdfText);
}

// =============================================================================
// CommSec trade confirmation parser
// =============================================================================

/**
 * Parses CommSec HTML-rendered trade confirmations (exported as PDF).
 *
 * Example confirmation text (simplified):
 *   Contract Note
 *   Date: 16 Jan 2024
 *   Order Number: B0012345
 *   Security: COMMONWEALTH BANK OF AUSTRALIA (CBA)
 *   Transaction: BUY  100 Ordinary Shares @ $105.500
 *   Brokerage: $9.95
 *   Total Amount: $10,559.95
 */
@Component
class CommSecPdfParser implements TradeConfirmationParser {

    private static final Logger log = LoggerFactory.getLogger(CommSecPdfParser.class);

    private static final Pattern DATE_PAT  = Pattern.compile(
        "Date[:\\s]+(\\d{1,2}\\s+\\w+\\s+\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_PAT = Pattern.compile(
        "Order\\s*(?:Number|No\\.?)[:\\s]+([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY_PAT   = Pattern.compile(
        "(BUY|SELL)\\s+([\\d,]+)\\s+.*?\\(([A-Z]{2,5})\\).*?@\\s*\\$?([\\d,.]+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern FEE_PAT   = Pattern.compile(
        "Brokerage[:\\s]+\\$?([\\d,.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_PAT = Pattern.compile(
        "Total\\s+Amount[:\\s]+\\$?([\\d,.]+)", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("d MMM yyyy");

    @Override public String brokerId() { return "COMMSEC"; }

    @Override
    public boolean canParse(String text) {
        return text.contains("CommSec") || text.contains("Commonwealth Securities");
    }

    @Override
    public Optional<ParsedTrade> parse(String text) {
        try {
            Matcher buyMatcher = BUY_PAT.matcher(text);
            if (!buyMatcher.find()) {
                log.debug("CommSec parser: could not find trade line");
                return Optional.empty();
            }

            String tradeType = buyMatcher.group(1).toUpperCase();
            BigDecimal qty   = new BigDecimal(buyMatcher.group(2).replace(",", ""));
            String ticker    = buyMatcher.group(3).toUpperCase();
            BigDecimal price = new BigDecimal(buyMatcher.group(4).replace(",", ""));

            BigDecimal fees  = PdfTradeExtractor.extractDecimal(FEE_PAT,   text).orElse(BigDecimal.ZERO);
            BigDecimal total = PdfTradeExtractor.extractDecimal(TOTAL_PAT, text)
                .orElse(price.multiply(qty).add(fees));

            LocalDate date = PdfTradeExtractor.extractDate(DATE_PAT, text, DATE_FMT)
                .orElse(LocalDate.now());

            String orderNo = PdfTradeExtractor.extractString(ORDER_PAT, text).orElse("");

            // Confidence: higher if we found all fields
            double confidence = 0.5
                + (fees.compareTo(BigDecimal.ZERO) > 0 ? 0.15 : 0)
                + (!orderNo.isEmpty() ? 0.15 : 0)
                + (total.compareTo(BigDecimal.ZERO) > 0 ? 0.20 : 0);

            return Optional.of(new ParsedTrade(
                ticker, "ASX", tradeType, qty, price, fees, total,
                "AUD", date, orderNo, confidence
            ));
        } catch (Exception e) {
            log.warn("CommSec parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

// =============================================================================
// SelfWealth trade confirmation parser
// =============================================================================

@Component
class SelfWealthPdfParser implements TradeConfirmationParser {

    private static final Logger log = LoggerFactory.getLogger(SelfWealthPdfParser.class);

    private static final Pattern TRADE_PAT = Pattern.compile(
        "(Buy|Sell)\\s+([\\d,]+)\\s+([A-Z]{2,5})\\s+@\\s*\\$?([\\d,.]+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PAT  = Pattern.compile(
        "Settlement Date[:\\s]+(\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEE_PAT   = Pattern.compile(
        "Brokerage[:\\s]+\\$?([\\d,.]+)", Pattern.CASE_INSENSITIVE);

    @Override public String brokerId() { return "SELFWEALTH"; }

    @Override
    public boolean canParse(String text) {
        return text.contains("SelfWealth") || text.contains("Self Wealth");
    }

    @Override
    public Optional<ParsedTrade> parse(String text) {
        try {
            Matcher m = TRADE_PAT.matcher(text);
            if (!m.find()) return Optional.empty();

            String tradeType = m.group(1).toUpperCase();
            BigDecimal qty   = new BigDecimal(m.group(2).replace(",", ""));
            String ticker    = m.group(3).toUpperCase();
            BigDecimal price = new BigDecimal(m.group(4).replace(",", ""));
            BigDecimal fees  = PdfTradeExtractor.extractDecimal(FEE_PAT, text).orElse(new BigDecimal("9.50"));

            LocalDate date = PdfTradeExtractor.extractDate(DATE_PAT, text,
                DateTimeFormatter.ofPattern("dd/MM/yyyy")).orElse(LocalDate.now());

            return Optional.of(new ParsedTrade(
                ticker, "ASX", tradeType, qty, price, fees,
                price.multiply(qty).add(fees), "AUD", date, "", 0.75
            ));
        } catch (Exception e) {
            log.warn("SelfWealth parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

// =============================================================================
// PDF text extractor + dispatcher
// =============================================================================

@Component
public class PdfTradeExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTradeExtractor.class);

    private final List<TradeConfirmationParser> parsers;

    public PdfTradeExtractor(List<TradeConfirmationParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Extracts text from a PDF stream, detects the broker, and delegates to
     * the appropriate parser.
     *
     * @param inputStream  PDF bytes from MinIO
     * @return ExtractionResult with best-effort parsed trade and confidence score
     */
    public ExtractionResult extract(InputStream inputStream) {
        String text;
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(doc);
            }
        } catch (Exception e) {
            log.error("PDF text extraction failed: {}", e.getMessage());
            return ExtractionResult.failed("Could not read PDF: " + e.getMessage());
        }

        String brokerDetected = detectBroker(text);

        for (TradeConfirmationParser parser : parsers) {
            if (parser.canParse(text)) {
                Optional<ParsedTrade> result = parser.parse(text);
                if (result.isPresent()) {
                    log.info("PDF parsed by {} with confidence {:.0f}%%",
                        parser.brokerId(), result.get().confidence() * 100);
                    return ExtractionResult.success(result.get(), brokerDetected);
                }
            }
        }

        log.warn("No parser could extract a trade from the PDF (broker detected: {})", brokerDetected);
        return ExtractionResult.failed("Could not extract trade data. Please enter manually.");
    }

    private String detectBroker(String text) {
        if (text.contains("CommSec") || text.contains("Commonwealth Securities")) return "COMMSEC";
        if (text.contains("SelfWealth"))       return "SELFWEALTH";
        if (text.contains("Interactive"))      return "IBKR";
        if (text.contains("Stake"))            return "STAKE";
        if (text.contains("Pearler"))          return "PEARLER";
        if (text.contains("nabtrade"))         return "NABTRADE";
        return "UNKNOWN";
    }

    // ── Shared utility methods ────────────────────────────────────────────────

    static Optional<BigDecimal> extractDecimal(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(m.group(1).replace(",", "")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<LocalDate> extractDate(Pattern p, String text, DateTimeFormatter fmt) {
        Matcher m = p.matcher(text);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(m.group(1).trim(), fmt));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<String> extractString(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public record ExtractionResult(
        boolean success,
        ParsedTrade trade,         // null if failed
        String brokerDetected,
        String errorMessage
    ) {
        static ExtractionResult success(ParsedTrade t, String broker) {
            return new ExtractionResult(true, t, broker, null);
        }
        static ExtractionResult failed(String msg) {
            return new ExtractionResult(false, null, "UNKNOWN", msg);
        }
    }
}
