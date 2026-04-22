package com.tradetracker.portfolio.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tax_parcels")
public class TaxParcel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_trade_id", nullable = false)
    private TradeEvent sourceTrade;

    /** Original quantity acquired. Immutable after creation. */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    /** Remaining quantity not yet disposed. Decremented on each SELL match. */
    @Column(name = "quantity_remaining", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantityRemaining;

    /**
     * Acquisition cost per unit INCLUDING fees, in trade currency.
     * Split-adjusted retroactively by the corporate actions processor.
     */
    @Column(name = "cost_per_unit", nullable = false, precision = 20, scale = 8)
    private BigDecimal costPerUnit;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "fx_rate_to_base", precision = 20, scale = 8)
    private BigDecimal fxRateToBase;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "disposal_date")
    private LocalDate disposalDate;

    @Column(name = "is_fully_disposed", nullable = false)
    private boolean fullyDisposed = false;

    protected TaxParcel() {}

    public TaxParcel(Portfolio portfolio,
                     Security security,
                     TradeEvent sourceTrade,
                     BigDecimal quantity,
                     BigDecimal costPerUnit,
                     String currency,
                     LocalDate acquisitionDate) {
        this.portfolio        = portfolio;
        this.security         = security;
        this.sourceTrade      = sourceTrade;
        this.quantity         = quantity;
        this.quantityRemaining = quantity;
        this.costPerUnit      = costPerUnit;
        this.currency         = currency;
        this.acquisitionDate  = acquisitionDate;
    }

    // ── Domain logic ─────────────────────────────────────────────────────────

    /**
     * Reduces quantityRemaining after a disposal.
     * Marks as fully disposed when nothing remains.
     *
     * @param disposed   quantity being disposed (must not exceed quantityRemaining)
     * @param disposalDate date of the SELL event
     */
    public void recordDisposal(BigDecimal disposed, LocalDate disposalDate) {
        if (disposed.compareTo(quantityRemaining) > 0) {
            throw new IllegalArgumentException(
                "Cannot dispose %.8f from parcel with only %.8f remaining"
                    .formatted(disposed.doubleValue(), quantityRemaining.doubleValue())
            );
        }
        this.quantityRemaining = quantityRemaining.subtract(disposed);
        if (this.quantityRemaining.compareTo(BigDecimal.ZERO) == 0) {
            this.fullyDisposed = true;
            this.disposalDate  = disposalDate;
        }
    }

    /**
     * ATO 50% CGT discount: parcel must have been held for strictly more than 12 months.
     */
    public boolean isEligibleForCgtDiscount(LocalDate disposalDate) {
        return acquisitionDate.plusYears(1).isBefore(disposalDate);
    }

    public BigDecimal totalCostBase() {
        return costPerUnit.multiply(quantity);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Portfolio getPortfolio()          { return portfolio; }
    public Security getSecurity()            { return security; }
    public TradeEvent getSourceTrade()       { return sourceTrade; }
    public BigDecimal getQuantity()          { return quantity; }
    public BigDecimal getQuantityRemaining() { return quantityRemaining; }
    public BigDecimal getCostPerUnit()       { return costPerUnit; }
    public String getCurrency()              { return currency; }
    public BigDecimal getFxRateToBase()      { return fxRateToBase; }
    public LocalDate getAcquisitionDate()    { return acquisitionDate; }
    public LocalDate getDisposalDate()       { return disposalDate; }
    public boolean isFullyDisposed()         { return fullyDisposed; }

    // ── Package-private setters for corporate actions processor ───────────────

    public void setCostPerUnit(BigDecimal costPerUnit)   { this.costPerUnit = costPerUnit; }
    public void setQuantity(BigDecimal quantity)          { this.quantity = quantity; }
    public void setQuantityRemaining(BigDecimal qty)      { this.quantityRemaining = qty; }
    public void setFxRateToBase(BigDecimal fx)            { this.fxRateToBase = fx; }
}
