package com.tradetracker.portfolio.matching;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// ===========================================================================
// FIFO — First In, First Out
// ===========================================================================
@Component("FIFO")
class FifoMatchingStrategy implements ParcelMatchingStrategy {
    @Override public String strategyName() { return "FIFO"; }
    @Override
    public List<ParcelAllocation> match(SellEvent sell, List<OpenParcel> parcels) {
        return allocate(sell, parcels.stream()
            .sorted(Comparator.comparing(OpenParcel::acquisitionDate)
                .thenComparing(OpenParcel::parcelId))
            .toList());
    }
}

// ===========================================================================
// LIFO — Last In, First Out
// ===========================================================================
@Component("LIFO")
class LifoMatchingStrategy implements ParcelMatchingStrategy {
    @Override public String strategyName() { return "LIFO"; }
    @Override
    public List<ParcelAllocation> match(SellEvent sell, List<OpenParcel> parcels) {
        return allocate(sell, parcels.stream()
            .sorted(Comparator.comparing(OpenParcel::acquisitionDate).reversed()
                .thenComparing(OpenParcel::parcelId))
            .toList());
    }
}

// ===========================================================================
// MINIMISE_CGT — Australian tax-optimal ordering
// Priority: losses → discountable gains (smallest first) → short-term gains
// ===========================================================================
@Component("MINIMISE_CGT")
class MinimiseCgtMatchingStrategy implements ParcelMatchingStrategy {
    @Override public String strategyName() { return "MINIMISE_CGT"; }
    @Override
    public List<ParcelAllocation> match(SellEvent sell, List<OpenParcel> parcels) {
        return allocate(sell, parcels.stream()
            .sorted(Comparator
                .comparingInt((OpenParcel p) -> bucket(p, sell))
                .thenComparing(p -> p.gainPerUnit(sell.pricePerUnit()).abs()))
            .toList());
    }

    private int bucket(OpenParcel p, SellEvent sell) {
        if (p.gainPerUnit(sell.pricePerUnit()).compareTo(BigDecimal.ZERO) < 0) return 0;
        if (p.isEligibleForCgtDiscount(sell.tradeDate())) return 1;
        return 2;
    }
}
