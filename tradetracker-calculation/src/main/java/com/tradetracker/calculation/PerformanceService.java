package com.tradetracker.calculation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Computes portfolio performance metrics by joining portfolio_snapshots
 * with trade_events (cashflows) for the requested time window.
 *
 * Cached in Redis — evicted nightly after the snapshot job runs.
 */
@Service
public class PerformanceService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceService.class);

    private final JdbcClient jdbc;
    private final ReturnsCalculationService calc;

    public PerformanceService(JdbcClient jdbc, ReturnsCalculationService calc) {
        this.jdbc = jdbc;
        this.calc = calc;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    // @Cacheable disabled - Redis serialization issues with record types
    public PerformanceResult getPerformance(UUID portfolioId, String period) {
        DateRange range = resolvePeriod(period);
        log.debug("Computing performance for {} period={} ({} → {})",
            portfolioId, period, range.from(), range.to());

        List<SnapshotRow> snapshots = fetchSnapshots(portfolioId, range);

        // If no snapshots yet (portfolio was created today, nightly job hasn't run),
        // synthesise a single snapshot from current holdings + cost base so the
        // dashboard shows something meaningful immediately.
        if (snapshots.isEmpty()) {
            snapshots = synthesiseSingleSnapshot(portfolioId);
            if (snapshots.isEmpty()) return PerformanceResult.empty(period);
        }

        List<CashFlowRow> cashFlows = fetchCashFlows(portfolioId, range);

        // ── TWR ──────────────────────────────────────────────────────────────
        // Build sub-periods: a new sub-period starts at each external cashflow
        List<ReturnsCalculationService.SubPeriod> subPeriods =
            buildSubPeriods(snapshots, cashFlows);
        BigDecimal twr = calc.calculateTwr(subPeriods);

        // ── MWR ──────────────────────────────────────────────────────────────
        // Convert cashflows + final value into IRR input
        List<ReturnsCalculationService.CashFlow> irr =
            buildIrrCashFlows(snapshots, cashFlows, range);
        BigDecimal mwr = Optional.ofNullable(calc.calculateMwr(irr))
            .orElse(BigDecimal.ZERO);

        // ── Time series for chart ────────────────────────────────────────────
        List<PerformancePoint> timeSeries = buildTimeSeries(snapshots, twr);

        BigDecimal openingValue = snapshots.getFirst().marketValue();
        BigDecimal closingValue = snapshots.getLast().marketValue();
        BigDecimal netCashFlow  = cashFlows.stream()
            .map(CashFlowRow::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PerformanceResult(
            period, twr, mwr,
            openingValue, closingValue, netCashFlow,
            timeSeries
        );
    }

    // ── Sub-period construction for TWR ──────────────────────────────────────

    /**
     * Splits the snapshot series into holding-period returns at each
     * external cashflow (deposits/withdrawals).
     *
     * Between cashflow events we have a simple HPR = endValue / startValue - 1.
     * At a cashflow event the denominator is startValue + cashflow.
     */
    private List<ReturnsCalculationService.SubPeriod> buildSubPeriods(
            List<SnapshotRow> snapshots, List<CashFlowRow> cashFlows) {

        if (snapshots.size() < 2) return List.of();

        // Index cashflows by date for O(1) lookup
        Map<LocalDate, BigDecimal> cfByDate = new HashMap<>();
        for (var cf : cashFlows) {
            cfByDate.merge(cf.tradeDate(), cf.amount(), BigDecimal::add);
        }

        List<ReturnsCalculationService.SubPeriod> subPeriods = new ArrayList<>();
        BigDecimal startValue = snapshots.getFirst().marketValue();

        for (int i = 1; i < snapshots.size(); i++) {
            SnapshotRow snap = snapshots.get(i);
            BigDecimal cf    = cfByDate.getOrDefault(snap.snapshotDate(), BigDecimal.ZERO);

            subPeriods.add(new ReturnsCalculationService.SubPeriod(
                startValue, snap.marketValue(), cf));

            startValue = snap.marketValue();
        }

        return subPeriods;
    }

    /**
     * Builds the IRR cashflow stream for MWR calculation.
     * Opening value is treated as an outflow; closing value as an inflow.
     */
    private List<ReturnsCalculationService.CashFlow> buildIrrCashFlows(
            List<SnapshotRow> snapshots,
            List<CashFlowRow> cashFlows,
            DateRange range) {

        List<ReturnsCalculationService.CashFlow> irr = new ArrayList<>();

        // Initial investment (outflow = negative)
        irr.add(new ReturnsCalculationService.CashFlow(
            -snapshots.getFirst().marketValue().doubleValue(), 0.0));

        // Intermediate cashflows at their fractional year position
        LocalDate from = range.from();
        double totalDays = range.from().until(range.to(),
            java.time.temporal.ChronoUnit.DAYS);

        for (var cf : cashFlows) {
            double tYears = from.until(cf.tradeDate(),
                java.time.temporal.ChronoUnit.DAYS) / 365.25;
            irr.add(new ReturnsCalculationService.CashFlow(
                -cf.amount().doubleValue(), tYears));   // inflow to portfolio = outflow to investor
        }

        // Final portfolio value (inflow = positive)
        irr.add(new ReturnsCalculationService.CashFlow(
            snapshots.getLast().marketValue().doubleValue(),
            totalDays / 365.25));

        return irr;
    }

    /**
     * Builds the cumulative TWR time-series for the dashboard chart.
     * Each point shows both absolute value and chain-linked TWR to that date.
     */
    private List<PerformancePoint> buildTimeSeries(
            List<SnapshotRow> snapshots, BigDecimal finalTwr) {

        if (snapshots.isEmpty()) return List.of();

        List<PerformancePoint> series = new ArrayList<>(snapshots.size());
        BigDecimal cumulativeTwr = BigDecimal.ZERO;
        BigDecimal startValue    = snapshots.getFirst().marketValue();

        for (SnapshotRow snap : snapshots) {
            if (startValue.compareTo(BigDecimal.ZERO) != 0) {
                cumulativeTwr = snap.marketValue()
                    .divide(startValue, 8, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);
            }
            series.add(new PerformancePoint(snap.snapshotDate(),
                snap.marketValue(), cumulativeTwr));
        }

        return series;
    }

    // ── Data access ───────────────────────────────────────────────────────────

    /**
     * When no historical snapshots exist, synthesise a current-value snapshot
     * by querying open parcels × latest prices directly.
     * Returns a single-point list so the caller can at least show today's value.
     */
    private List<SnapshotRow> synthesiseSingleSnapshot(UUID portfolioId) {
        return jdbc.sql("""
            SELECT
                CURRENT_DATE AS snapshot_date,
                COALESCE(SUM(tp.quantity_remaining * COALESCE(p.adjusted_close, tp.cost_per_unit)), 0) AS market_value,
                COALESCE(SUM(tp.quantity_remaining * tp.cost_per_unit), 0) AS cost_base
            FROM tax_parcels tp
            LEFT JOIN LATERAL (
                SELECT adjusted_close
                FROM security_prices sp
                WHERE sp.security_id = tp.security_id
                ORDER BY sp.price_date DESC
                LIMIT 1
            ) p ON true
            WHERE tp.portfolio_id = :pid
              AND tp.is_fully_disposed = false
            """)
            .param("pid", portfolioId)
            .query((rs, rowNum) -> new SnapshotRow(
                rs.getObject("snapshot_date", LocalDate.class),
                rs.getBigDecimal("market_value"),
                rs.getBigDecimal("cost_base")
            ))
            .list()
            .stream()
            .filter(s -> s.marketValue().compareTo(BigDecimal.ZERO) > 0)
            .toList();
    }

    private List<SnapshotRow> fetchSnapshots(UUID portfolioId, DateRange range) {
        return jdbc.sql("""
            SELECT snapshot_date, market_value, cost_base
            FROM portfolio_snapshots
            WHERE portfolio_id = :id
              AND snapshot_date BETWEEN :from AND :to
            ORDER BY snapshot_date ASC
            """)
            .param("id",   portfolioId)
            .param("from", range.from())
            .param("to",   range.to())
            .query((rs, rowNum) -> new SnapshotRow(
                rs.getObject("snapshot_date", LocalDate.class),
                rs.getBigDecimal("market_value"),
                rs.getBigDecimal("cost_base")
            ))
            .list();
    }

    private List<CashFlowRow> fetchCashFlows(UUID portfolioId, DateRange range) {
        // External cashflows: deposits (TRANSFER_IN) and withdrawals (TRANSFER_OUT)
        return jdbc.sql("""
            SELECT trade_date,
                   CASE trade_type
                     WHEN 'TRANSFER_IN'  THEN  (price * quantity + fees)
                     WHEN 'TRANSFER_OUT' THEN -(price * quantity - fees)
                     ELSE 0
                   END AS amount
            FROM trade_events
            WHERE portfolio_id = :id
              AND trade_type IN ('TRANSFER_IN','TRANSFER_OUT')
              AND trade_date BETWEEN :from AND :to
            ORDER BY trade_date
            """)
            .param("id",   portfolioId)
            .param("from", range.from())
            .param("to",   range.to())
            .query((rs, rowNum) -> new CashFlowRow(
                rs.getObject("trade_date", LocalDate.class),
                rs.getBigDecimal("amount")
            ))
            .list();
    }

    // ── Period resolution ─────────────────────────────────────────────────────

    private DateRange resolvePeriod(String period) {
        LocalDate to   = LocalDate.now();
        LocalDate from = switch (period.toUpperCase()) {
            case "1M"  -> to.minusMonths(1);
            case "3M"  -> to.minusMonths(3);
            case "6M"  -> to.minusMonths(6);
            case "YTD" -> LocalDate.of(to.getYear(), 1, 1);
            case "1Y"  -> to.minusYears(1);
            case "3Y"  -> to.minusYears(3);
            case "5Y"  -> to.minusYears(5);
            case "ALL" -> LocalDate.of(2000, 1, 1);
            default    -> to.minusYears(1);
        };
        return new DateRange(from, to);
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    private record SnapshotRow(LocalDate snapshotDate, BigDecimal marketValue, BigDecimal costBase) {}
    private record CashFlowRow(LocalDate tradeDate, BigDecimal amount) {}
    private record DateRange(LocalDate from, LocalDate to) {}

    public record PerformancePoint(LocalDate date, BigDecimal value, BigDecimal twr) {}

    public record PerformanceResult(
        String period,
        BigDecimal twr,
        BigDecimal mwr,
        BigDecimal openingValue,
        BigDecimal closingValue,
        BigDecimal netCashFlow,
        List<PerformancePoint> timeSeries
    ) {
        static PerformanceResult empty(String period) {
            return new PerformanceResult(period,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of());
        }
    }
}
