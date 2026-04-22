package com.tradetracker.portfolio.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Parcel matching strategies")
class ParcelMatchingStrategyTest {

    private static final UUID P1 = UUID.randomUUID();
    private static final UUID P2 = UUID.randomUUID();
    private static final UUID P3 = UUID.randomUUID();

    private FifoMatchingStrategy   fifo;
    private LifoMatchingStrategy   lifo;
    private MinimiseCgtMatchingStrategy minimise;

    @BeforeEach
    void setUp() {
        fifo     = new FifoMatchingStrategy();
        lifo     = new LifoMatchingStrategy();
        minimise = new MinimiseCgtMatchingStrategy();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ParcelMatchingStrategy.OpenParcel parcel(UUID id, String qty,
                                                              String cost, String acquiredDate) {
        return new ParcelMatchingStrategy.OpenParcel(
            id, bd(qty), bd(cost), LocalDate.parse(acquiredDate));
    }

    private static ParcelMatchingStrategy.SellEvent sell(String qty, String price,
                                                          String tradeDate) {
        return new ParcelMatchingStrategy.SellEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            bd(qty), bd(price), LocalDate.parse(tradeDate));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    // ── FIFO ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("FIFO")
    class FifoTests {

        @Test @DisplayName("strategyName returns FIFO")
        void strategyName() {
            assertThat(fifo.strategyName()).isEqualTo("FIFO");
        }

        @Test @DisplayName("Oldest parcel consumed first")
        void oldest_parcel_first() {
            var parcels = List.of(
                parcel(P2, "100", "30.00", "2023-06-01"),   // newer
                parcel(P1, "100", "25.00", "2022-01-15")    // oldest
            );
            var sell = sell("50", "35.00", "2024-01-01");

            var allocations = fifo.match(sell, parcels);

            assertThat(allocations).hasSize(1);
            assertThat(allocations.get(0).parcelId()).isEqualTo(P1);  // oldest
            assertThat(allocations.get(0).quantityToDispose()).isEqualByComparingTo("50");
        }

        @Test @DisplayName("Spans multiple parcels when first is insufficient")
        void spans_multiple_parcels() {
            var parcels = List.of(
                parcel(P1, "30",  "25.00", "2022-01-01"),
                parcel(P2, "70",  "28.00", "2023-01-01"),
                parcel(P3, "100", "30.00", "2024-01-01")
            );
            var sell = sell("80", "35.00", "2024-06-01");

            var allocs = fifo.match(sell, parcels);

            assertThat(allocs).hasSize(2);
            assertThat(allocs.get(0).parcelId()).isEqualTo(P1);
            assertThat(allocs.get(0).quantityToDispose()).isEqualByComparingTo("30");
            assertThat(allocs.get(1).parcelId()).isEqualTo(P2);
            assertThat(allocs.get(1).quantityToDispose()).isEqualByComparingTo("50");
        }

        @Test @DisplayName("Exact match — parcel fully consumed")
        void exact_full_parcel_consumed() {
            var parcels = List.of(parcel(P1, "100", "25.00", "2022-01-01"));
            var allocs = fifo.match(sell("100", "30.00", "2024-01-01"), parcels);
            assertThat(allocs.get(0).quantityToDispose()).isEqualByComparingTo("100");
        }

        @Test @DisplayName("Insufficient holdings throws InsufficientHoldingsException")
        void insufficient_holdings_throws() {
            var parcels = List.of(parcel(P1, "50", "25.00", "2022-01-01"));
            assertThatThrownBy(() -> fifo.match(sell("100", "30.00", "2024-01-01"), parcels))
                .isInstanceOf(InsufficientHoldingsException.class)
                .hasMessageContaining("exceeds");
        }

        @Test @DisplayName("Empty parcel list throws InsufficientHoldingsException")
        void empty_parcel_list_throws() {
            assertThatThrownBy(() -> fifo.match(sell("1", "30.00", "2024-01-01"), List.of()))
                .isInstanceOf(InsufficientHoldingsException.class);
        }
    }

    // ── LIFO ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("LIFO")
    class LifoTests {

        @Test @DisplayName("strategyName returns LIFO")
        void strategyName() {
            assertThat(lifo.strategyName()).isEqualTo("LIFO");
        }

        @Test @DisplayName("Most recently acquired parcel consumed first")
        void newest_parcel_first() {
            var parcels = List.of(
                parcel(P1, "100", "25.00", "2022-01-15"),   // oldest
                parcel(P2, "100", "30.00", "2023-06-01")    // newest
            );
            var allocs = lifo.match(sell("50", "35.00", "2024-01-01"), parcels);

            assertThat(allocs.get(0).parcelId()).isEqualTo(P2);  // newest first
        }

        @Test @DisplayName("LIFO and FIFO give different results on multi-parcel sell")
        void lifo_differs_from_fifo() {
            var parcels = List.of(
                parcel(P1, "60", "25.00", "2022-01-01"),
                parcel(P2, "60", "35.00", "2023-06-01")
            );
            var sell = sell("80", "40.00", "2024-01-01");

            var fifoAllocs = fifo.match(sell, parcels);
            var lifoAllocs = lifo.match(sell, parcels);

            // FIFO: take 60 from P1 (oldest) then 20 from P2
            assertThat(fifoAllocs.get(0).parcelId()).isEqualTo(P1);
            assertThat(fifoAllocs.get(0).quantityToDispose()).isEqualByComparingTo("60");

            // LIFO: take 60 from P2 (newest) then 20 from P1
            assertThat(lifoAllocs.get(0).parcelId()).isEqualTo(P2);
            assertThat(lifoAllocs.get(0).quantityToDispose()).isEqualByComparingTo("60");
        }
    }

    // ── MINIMISE_CGT ──────────────────────────────────────────────────────────

    @Nested @DisplayName("MinimiseCGT")
    class MinimiseCgtTests {

        @Test @DisplayName("strategyName returns MINIMISE_CGT")
        void strategyName() {
            assertThat(minimise.strategyName()).isEqualTo("MINIMISE_CGT");
        }

        @Test @DisplayName("Capital loss parcel disposed before gain parcels")
        void loss_parcel_disposed_first() {
            LocalDate sell2024 = LocalDate.of(2024, 6, 1);

            var parcels = List.of(
                // P1: cost $40, price $35 → LOSS (-$5/unit)
                parcel(P1, "100", "40.00", "2023-01-01"),
                // P2: cost $25, price $35 → gain, held >12 months → discountable
                parcel(P2, "100", "25.00", "2022-01-01"),
                // P3: cost $30, price $35 → gain, held <12 months → short-term
                parcel(P3, "100", "30.00", "2024-01-01")
            );

            var sell = new ParcelMatchingStrategy.SellEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                bd("50"), bd("35.00"), sell2024);

            var allocs = minimise.match(sell, parcels);

            // P1 (loss) must come first
            assertThat(allocs.get(0).parcelId()).isEqualTo(P1);
        }

        @Test @DisplayName("Discountable gain disposed before short-term gain")
        void discount_eligible_before_short_term() {
            LocalDate sellDate = LocalDate.of(2024, 6, 1);

            var parcels = List.of(
                // P1: held < 12 months → short-term gain
                parcel(P1, "100", "30.00", "2024-01-01"),
                // P2: held > 12 months → CGT discount eligible
                parcel(P2, "100", "25.00", "2022-01-01")
            );

            var sell = new ParcelMatchingStrategy.SellEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                bd("50"), bd("35.00"), sellDate);

            var allocs = minimise.match(sell, parcels);

            // P2 (discountable) first, P1 (short-term) second
            assertThat(allocs.get(0).parcelId()).isEqualTo(P2);
        }

        @Test @DisplayName("Within same bucket, smallest gain disposed first")
        void smallest_gain_first_within_bucket() {
            LocalDate sellDate = LocalDate.of(2024, 6, 1);
            // Both parcels held < 12 months (short-term), different cost bases
            var parcels = List.of(
                // P1: cost $30, proceeds $35 → gain $5/unit (smaller)
                parcel(P1, "100", "30.00", "2024-01-02"),
                // P2: cost $20, proceeds $35 → gain $15/unit (larger)
                parcel(P2, "100", "20.00", "2024-01-01")
            );

            var sell = new ParcelMatchingStrategy.SellEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                bd("50"), bd("35.00"), sellDate);

            var allocs = minimise.match(sell, parcels);
            // P1 has smaller gain — should be disposed first to minimise tax
            assertThat(allocs.get(0).parcelId()).isEqualTo(P1);
        }

        @Test @DisplayName("ATO 12-month boundary: exactly 12 months is NOT eligible")
        void exactly_twelve_months_not_eligible() {
            // Acquired 2023-06-01, disposed 2024-06-01 = exactly 12 months → NOT eligible
            var parcel = parcel(P1, "100", "25.00", "2023-06-01");
            var disposeDate = LocalDate.of(2024, 6, 1);
            assertThat(parcel.isEligibleForCgtDiscount(disposeDate)).isFalse();
        }

        @Test @DisplayName("ATO 12-month boundary: one day over 12 months IS eligible")
        void one_day_over_twelve_months_eligible() {
            // Acquired 2023-06-01, disposed 2024-06-02 = 12 months + 1 day → eligible
            var parcel = parcel(P1, "100", "25.00", "2023-06-01");
            var disposeDate = LocalDate.of(2024, 6, 2);
            assertThat(parcel.isEligibleForCgtDiscount(disposeDate)).isTrue();
        }
    }

    // ── OpenParcel record ─────────────────────────────────────────────────────

    @Nested @DisplayName("OpenParcel value object")
    class OpenParcelTests {

        @Test @DisplayName("gainPerUnit calculates correctly")
        void gain_per_unit() {
            var p = parcel(P1, "100", "25.00", "2022-01-01");
            assertThat(p.gainPerUnit(bd("30.00"))).isEqualByComparingTo("5.00");
            assertThat(p.gainPerUnit(bd("20.00"))).isEqualByComparingTo("-5.00");
        }

        @Test @DisplayName("gainPerUnit with zero proceeds")
        void gain_per_unit_zero_proceeds() {
            var p = parcel(P1, "100", "25.00", "2022-01-01");
            assertThat(p.gainPerUnit(bd("0.00"))).isEqualByComparingTo("-25.00");
        }
    }

    // ── Allocation amounts ────────────────────────────────────────────────────

    @Test @DisplayName("Total allocated quantity always equals sell quantity")
    void total_allocation_equals_sell_quantity() {
        var parcels = List.of(
            parcel(P1, "30",  "25.00", "2022-01-01"),
            parcel(P2, "45",  "28.00", "2023-01-01"),
            parcel(P3, "100", "30.00", "2024-01-01")
        );
        var sell = sell("95", "35.00", "2024-06-01");

        for (var strategy : List.of(fifo, lifo, minimise)) {
            var allocs = strategy.match(sell, parcels);
            BigDecimal totalAllocated = allocs.stream()
                .map(ParcelMatchingStrategy.ParcelAllocation::quantityToDispose)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalAllocated)
                .as(strategy.strategyName() + " total allocation")
                .isEqualByComparingTo("95");
        }
    }

    @Test @DisplayName("Proceeds per unit always equals sell price across strategies")
    void proceeds_per_unit_is_sell_price() {
        var parcels = List.of(parcel(P1, "100", "25.00", "2022-01-01"));
        var sell = sell("50", "42.50", "2024-01-01");

        for (var strategy : List.of(fifo, lifo, minimise)) {
            var allocs = strategy.match(sell, parcels);
            assertThat(allocs.get(0).proceedsPerUnit())
                .as(strategy.strategyName())
                .isEqualByComparingTo("42.50");
        }
    }
}
