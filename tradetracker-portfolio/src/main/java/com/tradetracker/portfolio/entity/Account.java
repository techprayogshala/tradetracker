package com.tradetracker.portfolio.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String broker;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency = "AUD";

    protected Account() {}

    public Account(Portfolio portfolio, String name, String currency) {
        this.portfolio = portfolio;
        this.name      = name;
        this.currency  = currency;
    }

    public Portfolio getPortfolio()    { return portfolio; }
    public String getName()            { return name; }
    public String getBroker()          { return broker; }
    public String getAccountNumber()   { return accountNumber; }
    public String getCurrency()        { return currency; }

    public void setName(String name)           { this.name = name; }
    public void setBroker(String broker)       { this.broker = broker; }
    public void setAccountNumber(String num)   { this.accountNumber = num; }
    public void setCurrency(String currency)   { this.currency = currency; }
}
