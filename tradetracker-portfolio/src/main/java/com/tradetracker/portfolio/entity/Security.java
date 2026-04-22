package com.tradetracker.portfolio.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "securities",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "exchange"}))
public class Security extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(length = 255)
    private String name;

    @Column(length = 12, unique = true)
    private String isin;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "asset_class", nullable = false, length = 50)
    private String assetClass = "EQUITY";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Security() {}

    public Security(String ticker, String exchange, String currency) {
        this.ticker   = ticker.toUpperCase();
        this.exchange = exchange.toUpperCase();
        this.currency = currency.toUpperCase();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getTicker()     { return ticker; }
    public String getExchange()   { return exchange; }
    public String getName()       { return name; }
    public String getIsin()       { return isin; }
    public String getCurrency()   { return currency; }
    public String getAssetClass() { return assetClass; }
    public boolean isActive()     { return active; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setName(String name)           { this.name = name; }
    public void setIsin(String isin)           { this.isin = isin; }
    public void setAssetClass(String ac)       { this.assetClass = ac; }
    public void setActive(boolean active)      { this.active = active; }
    public void setCurrency(String currency)   { this.currency = currency; }
}
