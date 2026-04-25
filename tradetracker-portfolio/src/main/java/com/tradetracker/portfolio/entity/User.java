package com.tradetracker.portfolio.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "keycloak_sub", nullable = false, unique = true)
    private String keycloakSub;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "base_currency", nullable = false, columnDefinition = "char(3)")
    private String baseCurrency = "AUD";

    @Column(name = "tax_country", nullable = false, length = 2)
    private String taxCountry = "AU";

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Portfolio> portfolios = new ArrayList<>();

    protected User() {}

    public User(String keycloakSub, String email, String displayName) {
        this.keycloakSub = keycloakSub;
        this.email       = email;
        this.displayName = displayName;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getKeycloakSub()   { return keycloakSub; }
    public String getEmail()         { return email; }
    public String getDisplayName()   { return displayName; }
    public String getBaseCurrency()  { return baseCurrency; }
    public String getTaxCountry()    { return taxCountry; }
    public List<Portfolio> getPortfolios() { return portfolios; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setKeycloakSub(String keycloakSub) { this.keycloakSub = keycloakSub; }
    public void setEmail(String email)             { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setBaseCurrency(String currency)   { this.baseCurrency = currency; }
    public void setTaxCountry(String taxCountry)   { this.taxCountry = taxCountry; }
}
