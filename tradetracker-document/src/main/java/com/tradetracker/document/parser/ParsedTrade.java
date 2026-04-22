package com.tradetracker.document.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ParsedTrade {
    private final String ticker;
    private final String exchange;
    private final String tradeType;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal fees;
    private final BigDecimal totalCost;
    private final String currency;
    private final LocalDate tradeDate;
    private final String confirmationNumber;
    private final double confidence;

    public ParsedTrade(String ticker, String exchange, String tradeType,
        BigDecimal quantity, BigDecimal price, BigDecimal fees,
        BigDecimal totalCost, String currency, LocalDate tradeDate,
        String confirmationNumber, double confidence) {
        this.ticker = ticker;
        this.exchange = exchange;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.price = price;
        this.fees = fees;
        this.totalCost = totalCost;
        this.currency = currency;
        this.tradeDate = tradeDate;
        this.confirmationNumber = confirmationNumber;
        this.confidence = confidence;
    }

    public String ticker() { return ticker; }
    public String exchange() { return exchange; }
    public String tradeType() { return tradeType; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal price() { return price; }
    public BigDecimal fees() { return fees; }
    public BigDecimal totalCost() { return totalCost; }
    public String currency() { return currency; }
    public LocalDate tradeDate() { return tradeDate; }
    public String confirmationNumber() { return confirmationNumber; }
    public double confidence() { return confidence; }
}