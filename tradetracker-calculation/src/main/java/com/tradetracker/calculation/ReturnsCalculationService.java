package com.tradetracker.calculation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Core returns calculation engine.
 *
 * Implements:
 *   - Time-Weighted Return (TWR)  — eliminates cashflow timing distortion
 *   - Money-Weighted Return (MWR) — IRR, reflects actual investor experience
 */
@Service
public class ReturnsCalculationService {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final double CONVERGENCE_THRESHOLD = 1e-10;
    private static final int MAX_ITERATIONS = 100;

    // ── TWR ─────────────────────────────────────────────────────────────────

    /**
     * Time-Weighted Return.
     *
     * Algorithm:
     * 1. Split the period into sub-periods at each external cashflow event
     * 2. HPR = End Value / (Start Value + Cashflows during sub-period)
     * 3. Chain-link: TWR = Π(1 + HPRᵢ) - 1
     *
     * @param subPeriods ordered list of holding-period data points
     * @return TWR as a decimal (e.g. 0.15 = 15%)
     */
    public BigDecimal calculateTwr(List<SubPeriod> subPeriods) {
        if (subPeriods == null || subPeriods.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return subPeriods.stream()
            .map(sp -> ONE.add(sp.holdingPeriodReturn(), MC))
            .reduce(ONE, (a, b) -> a.multiply(b, MC))
            .subtract(ONE, MC);
    }

    // ── MWR (IRR) ────────────────────────────────────────────────────────────

    /**
     * Money-Weighted Return (Internal Rate of Return).
     *
     * Solves for r in: NPV = 0 = Σ CFᵢ / (1+r)^tᵢ
     * where tᵢ is the time in years from the start date.
     *
     * Uses Newton-Raphson with bisection fallback.
     *
     * @param cashFlows list of (amount, time_in_years) pairs.
     *                  Negative = outflow (investment), Positive = inflow (return).
     *                  The final portfolio value is the last positive cashflow.
     * @return MWR as a decimal, or null if no solution found
     */
    public BigDecimal calculateMwr(List<CashFlow> cashFlows) {
        if (cashFlows == null || cashFlows.size() < 2) {
            return BigDecimal.ZERO;
        }

        try {
            return BigDecimal.valueOf(newtonRaphson(cashFlows, 0.10));
        } catch (ConvergenceException e) {
            // Fall back to bisection search
            try {
                return BigDecimal.valueOf(bisectionSearch(cashFlows, -0.99, 10.0));
            } catch (ConvergenceException ex) {
                return null;  // IRR not solvable (e.g. all-loss portfolio)
            }
        }
    }

    private double newtonRaphson(List<CashFlow> cashFlows, double initialGuess)
            throws ConvergenceException {

        double r = initialGuess;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double npv  = npv(cashFlows, r);
            double dnpv = npvDerivative(cashFlows, r);

            if (Math.abs(dnpv) < 1e-15) {
                throw new ConvergenceException("Derivative too small");
            }

            double rNew = r - npv / dnpv;

            if (Math.abs(rNew - r) < CONVERGENCE_THRESHOLD) {
                return rNew;
            }
            r = rNew;
        }
        throw new ConvergenceException("Newton-Raphson did not converge");
    }

    private double bisectionSearch(List<CashFlow> cashFlows, double low, double high)
            throws ConvergenceException {

        for (int i = 0; i < MAX_ITERATIONS * 5; i++) {
            double mid = (low + high) / 2.0;
            double npvMid = npv(cashFlows, mid);

            if (Math.abs(npvMid) < CONVERGENCE_THRESHOLD) {
                return mid;
            }
            if (npvMid * npv(cashFlows, low) < 0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        throw new ConvergenceException("Bisection did not converge");
    }

    private double npv(List<CashFlow> cashFlows, double r) {
        double result = 0;
        for (CashFlow cf : cashFlows) {
            result += cf.amount() / Math.pow(1 + r, cf.timeInYears());
        }
        return result;
    }

    private double npvDerivative(List<CashFlow> cashFlows, double r) {
        double result = 0;
        for (CashFlow cf : cashFlows) {
            result -= cf.timeInYears() * cf.amount() / Math.pow(1 + r, cf.timeInYears() + 1);
        }
        return result;
    }

    // ── Value objects ────────────────────────────────────────────────────────

    /**
     * A single holding-period return for use in TWR chain-linking.
     *
     * @param startValue  portfolio value at the start of the sub-period
     * @param endValue    portfolio value at the end of the sub-period
     * @param cashFlows   net external cashflows during the sub-period (deposits - withdrawals)
     */
    public record SubPeriod(BigDecimal startValue, BigDecimal endValue, BigDecimal cashFlows) {
        public BigDecimal holdingPeriodReturn() {
            BigDecimal denominator = startValue.add(cashFlows, MC);
            if (denominator.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return endValue.divide(denominator, MC).subtract(ONE, MC);
        }
    }

    /**
     * A cashflow for use in MWR/IRR calculation.
     *
     * @param amount      positive = inflow, negative = outflow
     * @param timeInYears time from start date (fractional years)
     */
    public record CashFlow(double amount, double timeInYears) {}

    static class ConvergenceException extends Exception {
        ConvergenceException(String msg) { super(msg); }
    }
}
