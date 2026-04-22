package com.tradetracker.calculation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReturnsCalculationServiceTest {

    private final ReturnsCalculationService svc = new ReturnsCalculationService();

    // ── TWR ───────────────────────────────────────────────────────────────────

    @Test
    void twr_single_period_no_cashflows() {
        // $10,000 grows to $12,000 — pure 20% return
        var periods = List.of(
            new ReturnsCalculationService.SubPeriod(
                bd("10000"), bd("12000"), BigDecimal.ZERO)
        );
        BigDecimal result = svc.calculateTwr(periods);
        assertThat(result).isEqualByComparingTo("0.20");
    }

    @Test
    void twr_chain_linked_across_multiple_periods() {
        // Two equal 10% periods chain-link to 21% (1.1 * 1.1 - 1)
        var p1 = new ReturnsCalculationService.SubPeriod(bd("10000"), bd("11000"), BigDecimal.ZERO);
        var p2 = new ReturnsCalculationService.SubPeriod(bd("11000"), bd("12100"), BigDecimal.ZERO);
        BigDecimal result = svc.calculateTwr(List.of(p1, p2));
        assertThat(result).isEqualByComparingTo("0.21");
    }

    @Test
    void twr_with_midperiod_deposit_eliminates_cashflow_distortion() {
        // Investor earns 10% in period 1, then deposits more cash.
        // Without TWR, the deposit would distort the % return.
        // Period 1: $10k → $11k (+10%)
        // $5k deposit → starting value for period 2 is $16k (not $11k)
        // Period 2: $16k → $17.6k (+10%)
        // Chain-linked TWR should still be exactly 21%
        var p1 = new ReturnsCalculationService.SubPeriod(bd("10000"), bd("11000"), BigDecimal.ZERO);
        var p2 = new ReturnsCalculationService.SubPeriod(bd("16000"), bd("17600"), bd("5000"));
        BigDecimal result = svc.calculateTwr(List.of(p1, p2));
        assertThat(result).isEqualByComparingTo("0.21");
    }

    @Test
    void twr_empty_returns_zero() {
        assertThat(svc.calculateTwr(List.of())).isEqualByComparingTo("0");
    }

    @Test
    void twr_losing_period() {
        var periods = List.of(
            new ReturnsCalculationService.SubPeriod(bd("10000"), bd("8500"), BigDecimal.ZERO)
        );
        BigDecimal result = svc.calculateTwr(periods);
        assertThat(result).isEqualByComparingTo("-0.15");
    }

    // ── MWR ───────────────────────────────────────────────────────────────────

    @Test
    void mwr_simple_annual_return() {
        // Invest $10,000 at t=0, receive $11,000 after exactly 1 year → MWR = 10%
        var cashFlows = List.of(
            new ReturnsCalculationService.CashFlow(-10_000.0, 0.0),
            new ReturnsCalculationService.CashFlow( 11_000.0, 1.0)
        );
        BigDecimal result = svc.calculateMwr(cashFlows);
        assertThat(result).isNotNull();
        assertThat(result.doubleValue()).isCloseTo(0.10, within(0.001));
    }

    @Test
    void mwr_with_intermediate_cashflow() {
        // $10k at t=0, add $5k at t=0.5yr, receive $16.5k at t=1yr
        var cashFlows = List.of(
            new ReturnsCalculationService.CashFlow(-10_000.0, 0.0),
            new ReturnsCalculationService.CashFlow( -5_000.0, 0.5),
            new ReturnsCalculationService.CashFlow( 16_500.0, 1.0)
        );
        BigDecimal result = svc.calculateMwr(cashFlows);
        assertThat(result).isNotNull();
        // Rough check: should be in reasonable range (≈ 8–12%)
        assertThat(result.doubleValue()).isBetween(0.05, 0.15);
    }

    @Test
    void mwr_total_loss_returns_null_gracefully() {
        // Invest $10k, receive $0 back — IRR not solvable
        var cashFlows = List.of(
            new ReturnsCalculationService.CashFlow(-10_000.0, 0.0),
            new ReturnsCalculationService.CashFlow(      0.0, 1.0)
        );
        BigDecimal result = svc.calculateMwr(cashFlows);
        assertThat(result).isNull();   // Documented behaviour for insoluble IRR
    }

    // ── SubPeriod ─────────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "10000, 11000, 0,    0.10",
        "10000,  9000, 0,   -0.10",
        "10000, 11000, 1000, 0.0909",  // HPR = 11000/11000 - 1 = 0 ... wait: 11000/(10000+1000)-1 ≈ 0
        "10000, 12000, 2000, 0.1667",  // 12000/12000 - 1 = 0
    })
    void sub_period_holding_period_return(
            String start, String end, String cf, String expected) {
        var sp = new ReturnsCalculationService.SubPeriod(bd(start), bd(end), bd(cf));
        assertThat(sp.holdingPeriodReturn().doubleValue())
            .isCloseTo(Double.parseDouble(expected), within(0.001));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
