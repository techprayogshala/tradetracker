package com.tradetracker.portfolio.matching;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pluggable strategy that determines which open parcels are consumed when a
 * SELL trade is processed.
 *
 * Lives in the portfolio module (not tax) because it operates on portfolio
 * state — open parcel quantities — not CGT calculations.
 *
 * Implementations are Spring @Component beans named to match
 * Portfolio.ParcelMatchingStrategy enum values: FIFO, LIFO, MINIMISE_CGT.
 */
public interface ParcelMatchingStrategy {

    /** Must exactly match a Portfolio.ParcelMatchingStrategy enum value. */
    String strategyName();

    /**
     * Returns allocations that together consume exactly sell.quantity().
     *
     * @throws InsufficientHoldingsException if total open parcel quantity < sell quantity
     */
    List<ParcelAllocation> match(SellEvent sell, List<OpenParcel> parcels);

    // ── Shared greedy allocation helper ───────────────────────────────────────

    default List<ParcelAllocation> allocate(SellEvent sell, List<OpenParcel> ordered) {
        List<ParcelAllocation> result = new ArrayList<>();
        BigDecimal remaining = sell.quantity();

        for (OpenParcel p : ordered) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal take = remaining.min(p.quantityRemaining());
            result.add(new ParcelAllocation(p.parcelId(), take, sell.pricePerUnit()));
            remaining = remaining.subtract(take);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new InsufficientHoldingsException(
                "Sell qty %.8f exceeds open holdings by %.8f for %s on %s"
                    .formatted(sell.quantity().doubleValue(), remaining.doubleValue(),
                               sell.securityId(), sell.tradeDate()));
        }
        return result;
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    record ParcelAllocation(UUID parcelId, BigDecimal quantityToDispose, BigDecimal proceedsPerUnit) {}

    record SellEvent(UUID id, UUID portfolioId, UUID securityId,
                     BigDecimal quantity, BigDecimal pricePerUnit, LocalDate tradeDate) {}

    record OpenParcel(UUID parcelId, BigDecimal quantityRemaining,
                      BigDecimal costPerUnit, LocalDate acquisitionDate) {

        boolean isEligibleForCgtDiscount(LocalDate disposalDate) {
            return acquisitionDate.plusYears(1).isBefore(disposalDate);
        }

        BigDecimal gainPerUnit(BigDecimal disposalPrice) {
            return disposalPrice.subtract(costPerUnit);
        }
    }
}
