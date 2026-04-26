package com.tradetracker.tax;

import com.tradetracker.portfolio.entity.TaxParcel;
import com.tradetracker.portfolio.repository.TaxParcelRepository;
import com.tradetracker.portfolio.repository.PortfolioRepository;
import com.tradetracker.portfolio.service.PriceService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class TaxReportService {

    private final TaxParcelRepository parcelRepo;
    private final PortfolioRepository portfolioRepo;
    private final AustralianCgtService cgtService;
    private final PriceService priceService;
    private final JdbcClient jdbc;

    public TaxReportService(TaxParcelRepository parcelRepo, PortfolioRepository portfolioRepo,
                             AustralianCgtService cgtService, PriceService priceService,
                             JdbcClient jdbc) {
        this.parcelRepo    = parcelRepo;
        this.portfolioRepo = portfolioRepo;
        this.cgtService    = cgtService;
        this.priceService  = priceService;
        this.jdbc          = jdbc;
    }

    // ── CGT Summary ───────────────────────────────────────────────────────────

    @PersistenceContext
    private EntityManager em;

    public AustralianCgtService.TaxYearCgtSummary getCgtSummary(
            String keycloakSub, UUID portfolioId, int financialYear) {
        requireOwnership(keycloakSub, portfolioId);
        List<AustralianCgtService.CgtResult> results =
            fetchDisposals(portfolioId, financialYear).stream().map(this::toResult).toList();
        
        BigDecimal carriedForwardLoss = getCarriedForwardLoss(portfolioId, financialYear);
        var summary = cgtService.summariseTaxYear(results, carriedForwardLoss);
        
        return new AustralianCgtService.TaxYearCgtSummary(
            financialYear,
            summary.shortTermGains(),
            summary.longTermGains(),
            summary.totalDiscountableGains(),
            summary.totalCurrentYearLosses(),
            summary.priorYearLossesApplied(),
            summary.netAssessableCgt(),
            summary.lossesCarriedForward()
        );
    }

    private BigDecimal getCarriedForwardLoss(UUID portfolioId, int financialYear) {
        try {
            var result = em.createNativeQuery(
                "SELECT losses_carried_forward FROM cgt_year_summaries " +
                "WHERE portfolio_id = :pid AND financial_year = :fy")
                .setParameter("pid", portfolioId)
                .setParameter("fy", financialYear - 1)
                .getSingleResult();
            return result != null ? (BigDecimal) result : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ── CGT Events ────────────────────────────────────────────────────────────

    public List<CgtEventView> getCgtEvents(
            String keycloakSub, UUID portfolioId, int financialYear, String sort, String sortDir) {
        requireOwnership(keycloakSub, portfolioId);
        var events = fetchDisposals(portfolioId, financialYear).stream().map(d -> {
            var result = toResult(d);
            return new CgtEventView(d.disposalDate(), d.ticker(),
                d.quantityDisposed(), d.proceeds(), d.costBase(),
                result.grossGain(), result.discountApplied(), result.assessableGain(),
                d.acquisitionDate(),
                (int) ChronoUnit.DAYS.between(d.acquisitionDate(), d.disposalDate()));
        }).toList();

        Comparator<CgtEventView> cmp = switch (sort) {
            case "ticker" -> Comparator.comparing(CgtEventView::ticker);
            case "quantity" -> Comparator.comparing(CgtEventView::quantity);
            case "proceeds" -> Comparator.comparing(CgtEventView::proceeds);
            case "costBase" -> Comparator.comparing(CgtEventView::costBase);
            case "capitalGain" -> Comparator.comparing(CgtEventView::capitalGain);
            case "assessableGain" -> Comparator.comparing(CgtEventView::assessableGain);
            default -> Comparator.comparing(CgtEventView::disposalDate);
        };
        if ("desc".equalsIgnoreCase(sortDir)) {
            cmp = cmp.reversed();
        }
        return events.stream().sorted(cmp).toList();
    }

    // ── Open parcels ──────────────────────────────────────────────────────────

    public List<OpenParcelView> getOpenParcels(String keycloakSub, UUID portfolioId, String sort, String sortDir) {
        requireOwnership(keycloakSub, portfolioId);
        List<TaxParcel> parcels = parcelRepo.findAllOpenByPortfolio(portfolioId);
        if (parcels.isEmpty()) return List.of();

        List<UUID> secIds = parcels.stream().map(p -> p.getSecurity().getId()).distinct().toList();
        Map<UUID, BigDecimal> prices = priceService.getCurrentPrices(secIds);
        LocalDate today = LocalDate.now();

        var views = parcels.stream().map(p -> {
            BigDecimal price   = prices.getOrDefault(p.getSecurity().getId(), BigDecimal.ZERO);
            BigDecimal mkt     = price.multiply(p.getQuantityRemaining()).setScale(3, RoundingMode.HALF_UP);
            BigDecimal cost    = p.getCostPerUnit().multiply(p.getQuantityRemaining()).setScale(3, RoundingMode.HALF_UP);
            BigDecimal gain    = mkt.subtract(cost);
            BigDecimal gainPct = cost.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : gain.divide(cost, 6, RoundingMode.HALF_UP);
            return new OpenParcelView(p.getId(), p.getSecurity().getTicker(),
                p.getQuantityRemaining(), p.getCostPerUnit(), cost,
                p.getAcquisitionDate(),
                (int) ChronoUnit.DAYS.between(p.getAcquisitionDate(), today),
                p.isEligibleForCgtDiscount(today), price, gain, gainPct);
        }).toList();

        Comparator<OpenParcelView> cmp = switch (sort) {
            case "ticker" -> Comparator.comparing(OpenParcelView::ticker);
            case "quantity" -> Comparator.comparing(OpenParcelView::quantity);
            case "costPerUnit" -> Comparator.comparing(OpenParcelView::costPerUnit);
            case "totalCostBase" -> Comparator.comparing(OpenParcelView::totalCostBase);
            case "unrealisedGain" -> Comparator.comparing(OpenParcelView::unrealisedGain);
            default -> Comparator.comparing(OpenParcelView::acquisitionDate);
        };
        if ("desc".equalsIgnoreCase(sortDir)) {
            cmp = cmp.reversed();
        }
        return views.stream().sorted(cmp).toList();
    }

    // ── Dividend summary ──────────────────────────────────────────────────────

    public DividendSummaryView getDividendSummary(
            String keycloakSub, UUID portfolioId, int financialYear) {
        requireOwnership(keycloakSub, portfolioId);

        record Row(String ticker, BigDecimal cash, BigDecimal franking, BigDecimal withheld) {}
        List<Row> rows = jdbc.sql("""
            SELECT s.ticker,
                   SUM(d.amount) AS cash, SUM(d.franking_credits) AS franking,
                   SUM(d.tax_withheld) AS withheld
            FROM dividends d JOIN securities s ON s.id = d.security_id
            WHERE d.portfolio_id = :pid
              AND d.payment_date BETWEEN :s AND :e
            GROUP BY s.ticker ORDER BY s.ticker
            """)
            .param("pid", portfolioId)
            .param("s",   fyStart(financialYear))
            .param("e",   fyEnd(financialYear))
            .query((rs, rowNum) -> new Row(rs.getString("ticker"), rs.getBigDecimal("cash"),
                rs.getBigDecimal("franking"), rs.getBigDecimal("withheld")))
            .list();

        BigDecimal totCash    = sum(rows, Row::cash);
        BigDecimal totFranking= sum(rows, Row::franking);
        BigDecimal totWithheld= sum(rows, Row::withheld);

        List<DividendDetailView> details = rows.stream().map(r -> {
            BigDecimal grossed = r.cash().add(r.franking());
            BigDecimal pct     = grossed.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : r.franking().divide(grossed, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            return new DividendDetailView(r.ticker(), r.cash(), r.franking(), grossed, pct);
        }).toList();

        return new DividendSummaryView(financialYear, totCash, totFranking,
            totCash.add(totFranking), totWithheld, details);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<DisposalRecord> fetchDisposals(UUID portfolioId, int financialYear) {
        return jdbc.sql("""
            SELECT pd.disposal_date, s.ticker, pd.quantity_disposed,
                   pd.disposal_price * pd.quantity_disposed AS proceeds,
                   tp.cost_per_unit  * pd.quantity_disposed AS cost_base,
                   tp.acquisition_date
            FROM parcel_disposals pd
            JOIN tax_parcels tp ON tp.id = pd.parcel_id
            JOIN securities  s  ON s.id  = tp.security_id
            WHERE tp.portfolio_id = :pid
              AND pd.disposal_date BETWEEN :s AND :e
            ORDER BY pd.disposal_date, s.ticker
            """)
            .param("pid", portfolioId)
            .param("s",   fyStart(financialYear))
            .param("e",   fyEnd(financialYear))
            .query((rs, rowNum) -> new DisposalRecord(
                rs.getObject("disposal_date",   LocalDate.class),
                rs.getString("ticker"),
                rs.getBigDecimal("quantity_disposed"),
                rs.getBigDecimal("proceeds"),
                rs.getBigDecimal("cost_base"),
                rs.getObject("acquisition_date", LocalDate.class)))
            .list();
    }

    private AustralianCgtService.CgtResult toResult(DisposalRecord d) {
        BigDecimal pricePerUnit = d.quantityDisposed().compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : d.proceeds().divide(d.quantityDisposed(), 10, RoundingMode.HALF_UP);
        BigDecimal costPerUnit = d.quantityDisposed().compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : d.costBase().divide(d.quantityDisposed(), 10, RoundingMode.HALF_UP);

        var view       = new AustralianCgtService.TaxParcelView(UUID.randomUUID(), costPerUnit, d.acquisitionDate());
        var allocation = new AustralianCgtService.DisposalInput(UUID.randomUUID(), d.quantityDisposed(), pricePerUnit);
        return cgtService.calculateDisposal(allocation, view, d.disposalDate(), AustralianCgtService.EntityType.INDIVIDUAL);
    }

    private void requireOwnership(String sub, UUID portfolioId) {
        if (!portfolioRepo.existsByIdAndUserKeycloakSub(portfolioId, sub))
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
    }

    private static LocalDate fyStart(int y) { return LocalDate.of(y - 1, 7, 1); }
    private static LocalDate fyEnd(int y)   { return LocalDate.of(y, 6, 30); }

    private static <T> BigDecimal sum(List<T> list, java.util.function.Function<T, BigDecimal> fn) {
        return list.stream().map(fn).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record DisposalRecord(LocalDate disposalDate, String ticker, BigDecimal quantityDisposed,
                                   BigDecimal proceeds, BigDecimal costBase, LocalDate acquisitionDate) {}

    public record CgtEventView(LocalDate disposalDate, String ticker, BigDecimal quantity,
        BigDecimal proceeds, BigDecimal costBase, BigDecimal capitalGain,
        boolean discountApplied, BigDecimal assessableGain, LocalDate acquisitionDate, int holdingDays) {}

    public record OpenParcelView(UUID parcelId, String ticker, BigDecimal quantity,
        BigDecimal costPerUnit, BigDecimal totalCostBase, LocalDate acquisitionDate,
        int holdingDays, boolean cgtDiscountEligible, BigDecimal currentPrice,
        BigDecimal unrealisedGain, BigDecimal unrealisedGainPct) {}

    public record DividendSummaryView(int financialYear, BigDecimal totalCashDividends,
        BigDecimal totalFrankingCredits, BigDecimal totalGrossedUpIncome,
        BigDecimal totalTaxWithheld, List<DividendDetailView> byHolding) {}

    public record DividendDetailView(String ticker, BigDecimal cashDividends,
        BigDecimal frankingCredits, BigDecimal grossedUpAmount, BigDecimal frankingPercentage) {}
}
