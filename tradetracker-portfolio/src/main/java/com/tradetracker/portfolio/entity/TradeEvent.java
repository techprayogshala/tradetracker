package com.tradetracker.portfolio.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "trade_events")
public class TradeEvent extends BaseEntity {

    public enum TradeType {
        BUY, SELL, DIVIDEND, RETURN_OF_CAPITAL, TRANSFER_IN, TRANSFER_OUT
    }

    public enum Source {
        MANUAL, BROKER_API, PDF_IMPORT, CSV_IMPORT
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 30)
    private TradeType tradeType;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    /** FX rate to the portfolio's base currency at trade date. Null = same currency. */
    @Column(name = "fx_rate_to_base", precision = 20, scale = 8)
    private BigDecimal fxRateToBase;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Source source = Source.MANUAL;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    protected TradeEvent() {}

    private TradeEvent(Builder b) {
        this.account        = b.account;
        this.portfolio      = b.portfolio;
        this.security       = b.security;
        this.tradeType      = b.tradeType;
        this.quantity       = b.quantity;
        this.price          = b.price;
        this.fees           = b.fees;
        this.currency       = b.currency;
        this.fxRateToBase   = b.fxRateToBase;
        this.tradeDate      = b.tradeDate;
        this.settlementDate = b.settlementDate;
        this.notes          = b.notes;
        this.source         = b.source;
        this.externalRef    = b.externalRef;
    }

    // ── Derived ──────────────────────────────────────────────────────────────

    /** Total cost including fees, in trade currency. */
    public BigDecimal totalCost() {
        return price.multiply(quantity).add(fees);
    }

    /** Cost per unit including fees (used as parcel cost base). */
    public BigDecimal costPerUnit() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return totalCost().divide(quantity, 10, java.math.RoundingMode.HALF_UP);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Account getAccount()             { return account; }
    public Portfolio getPortfolio()         { return portfolio; }
    public Security getSecurity()           { return security; }
    public TradeType getTradeType()         { return tradeType; }
    public BigDecimal getQuantity()         { return quantity; }
    public BigDecimal getPrice()            { return price; }
    public BigDecimal getFees()             { return fees; }
    public String getCurrency()             { return currency; }
    public BigDecimal getFxRateToBase()     { return fxRateToBase; }
    public LocalDate getTradeDate()         { return tradeDate; }
    public LocalDate getSettlementDate()    { return settlementDate; }
    public String getNotes()                { return notes; }
    public Source getSource()               { return source; }
    public String getExternalRef()          { return externalRef; }

    public void setNotes(String notes)      { this.notes = notes; }
    public void setPrice(BigDecimal price)  { this.price = price; }
    public void setFees(BigDecimal fees)    { this.fees = fees; }
    public void setFxRateToBase(BigDecimal rate) { this.fxRateToBase = rate; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Account account;
        private Portfolio portfolio;
        private Security security;
        private TradeType tradeType;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal fees = BigDecimal.ZERO;
        private String currency;
        private BigDecimal fxRateToBase;
        private LocalDate tradeDate;
        private LocalDate settlementDate;
        private String notes;
        private Source source = Source.MANUAL;
        private String externalRef;

        public Builder account(Account a)               { this.account = a; return this; }
        public Builder portfolio(Portfolio p)            { this.portfolio = p; return this; }
        public Builder security(Security s)             { this.security = s; return this; }
        public Builder tradeType(TradeType t)           { this.tradeType = t; return this; }
        public Builder quantity(BigDecimal q)            { this.quantity = q; return this; }
        public Builder price(BigDecimal p)               { this.price = p; return this; }
        public Builder fees(BigDecimal f)                { this.fees = f; return this; }
        public Builder currency(String c)                { this.currency = c; return this; }
        public Builder fxRateToBase(BigDecimal fx)       { this.fxRateToBase = fx; return this; }
        public Builder tradeDate(LocalDate d)            { this.tradeDate = d; return this; }
        public Builder settlementDate(LocalDate d)       { this.settlementDate = d; return this; }
        public Builder notes(String n)                   { this.notes = n; return this; }
        public Builder source(Source s)                  { this.source = s; return this; }
        public Builder externalRef(String r)             { this.externalRef = r; return this; }

        public TradeEvent build() {
            java.util.Objects.requireNonNull(account,   "account is required");
            java.util.Objects.requireNonNull(portfolio, "portfolio is required");
            java.util.Objects.requireNonNull(security,  "security is required");
            java.util.Objects.requireNonNull(tradeType, "tradeType is required");
            java.util.Objects.requireNonNull(quantity,  "quantity is required");
            java.util.Objects.requireNonNull(price,     "price is required");
            java.util.Objects.requireNonNull(currency,  "currency is required");
            java.util.Objects.requireNonNull(tradeDate, "tradeDate is required");
            return new TradeEvent(this);
        }
    }
}
