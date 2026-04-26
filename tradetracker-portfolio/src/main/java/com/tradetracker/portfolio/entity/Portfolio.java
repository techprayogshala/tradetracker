package com.tradetracker.portfolio.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios")
public class Portfolio extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_currency", nullable = false, columnDefinition = "char(3)")
    private String baseCurrency = "AUD";

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_matching_strategy", nullable = false, length = 50)
    private ParcelMatchingStrategy parcelMatchingStrategy = ParcelMatchingStrategy.FIFO;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Account> accounts = new ArrayList<>();

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TradeEvent> tradeEvents = new ArrayList<>();

    public enum ParcelMatchingStrategy {
        FIFO, LIFO, MAXIMISE_GAIN, MINIMISE_GAIN, MINIMISE_CGT, SPECIFIC_PARCEL
    }

    protected Portfolio() {}

    public Portfolio(User user, String name, String baseCurrency) {
        this.user         = user;
        this.name         = name;
        this.baseCurrency = baseCurrency;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /** Creates the default portfolio for a new user. */
    public static Portfolio createDefault(User user) {
        Portfolio p = new Portfolio(user, "My Portfolio", user.getBaseCurrency());
        p.isDefault = true;
        return p;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public User getUser()                       { return user; }
    public String getName()                     { return name; }
    public String getDescription()              { return description; }
    public String getBaseCurrency()             { return baseCurrency; }
    public ParcelMatchingStrategy getParcelMatchingStrategy() { return parcelMatchingStrategy; }
    public boolean isDefault()                  { return isDefault; }
    public List<Account> getAccounts()          { return accounts; }
    public List<TradeEvent> getTradeEvents()    { return tradeEvents; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setName(String name)            { this.name = name; }
    public void setDescription(String desc)     { this.description = desc; }
    public void setBaseCurrency(String cur)     { this.baseCurrency = cur; }
    public void setParcelMatchingStrategy(ParcelMatchingStrategy s) { this.parcelMatchingStrategy = s; }
    public void setDefault(boolean isDefault)   { this.isDefault = isDefault; }
}
