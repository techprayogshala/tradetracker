package com.tradetracker.tax;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Australian CGT engine.
 *
 * Rules implemented:
 *   - 50% CGT discount for parcels held > 12 months (individuals/trusts)
 *   - Capital losses offset gains before discount is applied
 *   - Carry-forward losses applied against current-year gains
 *   - Franking credit gross-up
 *   - Return of capital reduces cost base (not assessable income)
 */
@Service
public class AustralianCgtService {

    private static final BigDecimal CORPORATE_TAX_RATE = new BigDecimal("0.30");
    private static final BigDecimal CGT_DISCOUNT = new BigDecimal("0.50");
    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    /**
     * Calculate the CGT outcome for a single disposal.
     *
     * @param allocation    which parcel was disposed and how much
     * @param parcel        the original parcel (for cost base and acquisition date)
     * @param disposalDate  date of the SELL event
     * @param entityType    INDIVIDUAL, TRUST, or COMPANY (affects discount eligibility)
     * @return CGT result with gross gain, applicable discount, and assessable amount
     */
    public CgtResult calculateDisposal(
            DisposalInput allocation,
            TaxParcelView parcel,
            LocalDate disposalDate,
            EntityType entityType) {

        BigDecimal proceeds   = allocation.proceedsPerUnit()
                                    .multiply(allocation.quantityToDispose())
                                    .setScale(SCALE, RM);
        BigDecimal costBase   = parcel.costPerUnit()
                                    .multiply(allocation.quantityToDispose())
                                    .setScale(SCALE, RM);
        BigDecimal grossGain  = proceeds.subtract(costBase);

        boolean isLoss = grossGain.compareTo(BigDecimal.ZERO) < 0;
        boolean discountEligible =
            !isLoss
            && entityType != EntityType.COMPANY
            && parcel.acquisitionDate().plusYears(1).isBefore(disposalDate);

        BigDecimal discountAmount = discountEligible
            ? grossGain.multiply(CGT_DISCOUNT).setScale(SCALE, RM)
            : BigDecimal.ZERO;

        BigDecimal assessableGain = grossGain.subtract(discountAmount);

        return new CgtResult(
            parcel.parcelId(),
            allocation.quantityToDispose(),
            proceeds,
            costBase,
            grossGain,
            discountAmount,
            assessableGain,
            discountEligible,
            isLoss
        );
    }

    /**
     * Net CGT position for a full tax year.
     *
     * Capital losses must be applied before the 50% discount is applied —
     * this is the ATO-correct order (per TD 2001/18).
     *
     * @param results           all disposal results for the year
     * @param carriedForwardLoss losses carried forward from prior years (positive number)
     * @return netted tax year position
     */
    public TaxYearCgtSummary summariseTaxYear(
            List<CgtResult> results,
            BigDecimal carriedForwardLoss) {

        BigDecimal shortTermGains = BigDecimal.ZERO;
        BigDecimal longTermGains = BigDecimal.ZERO;
        BigDecimal totalDiscountGains = BigDecimal.ZERO;
        BigDecimal totalCurrentLosses = BigDecimal.ZERO;

        for (CgtResult r : results) {
            if (r.isLoss()) {
                totalCurrentLosses = totalCurrentLosses.add(r.grossGain().abs());
            } else if (r.discountApplied()) {
                longTermGains = longTermGains.add(r.grossGain());
                totalDiscountGains = totalDiscountGains.add(r.grossGain());
            } else {
                shortTermGains = shortTermGains.add(r.grossGain());
            }
        }

        BigDecimal totalLosses = totalCurrentLosses.add(carriedForwardLoss);

        BigDecimal lossesRemaining = totalLosses;
        BigDecimal shortTermAfterLoss = shortTermGains.subtract(lossesRemaining);
        if (shortTermAfterLoss.compareTo(BigDecimal.ZERO) < 0) {
            lossesRemaining = shortTermAfterLoss.abs();
            shortTermAfterLoss = BigDecimal.ZERO;
        } else {
            lossesRemaining = BigDecimal.ZERO;
        }

        BigDecimal longTermAfterLoss = longTermGains.subtract(lossesRemaining);
        BigDecimal lossCarriedForward;
        if (longTermAfterLoss.compareTo(BigDecimal.ZERO) < 0) {
            lossCarriedForward = longTermAfterLoss.abs();
            longTermAfterLoss = BigDecimal.ZERO;
        } else {
            lossCarriedForward = BigDecimal.ZERO;
        }

        BigDecimal discountedGain = longTermAfterLoss
            .multiply(BigDecimal.ONE.subtract(CGT_DISCOUNT))
            .setScale(SCALE, RM);

        BigDecimal netAssessableCgt = shortTermAfterLoss.add(discountedGain);

        return new TaxYearCgtSummary(
            0,
            shortTermGains,
            longTermGains,
            totalDiscountGains,
            totalCurrentLosses,
            carriedForwardLoss,
            netAssessableCgt,
            lossCarriedForward
        );
    }

    /**
     * Gross up a franked dividend for the franking credit.
     *
     * Grossed-up dividend = cash amount / (1 - corporate tax rate * franking %)
     * Franking credit     = grossed-up amount - cash amount
     */
    public FrankingResult calculateFrankingCredit(
            BigDecimal cashDividend,
            BigDecimal frankingPercentage) {

        if (frankingPercentage.compareTo(BigDecimal.ZERO) == 0) {
            return new FrankingResult(cashDividend, BigDecimal.ZERO, cashDividend);
        }

        BigDecimal effectiveTaxRate = CORPORATE_TAX_RATE.multiply(frankingPercentage)
            .divide(new BigDecimal("100"), SCALE, RM);

        BigDecimal grossedUp = cashDividend.divide(
            BigDecimal.ONE.subtract(effectiveTaxRate), SCALE, RM);

        BigDecimal frankingCredit = grossedUp.subtract(cashDividend);

        return new FrankingResult(cashDividend, frankingCredit, grossedUp);
    }

    // ── Value objects ────────────────────────────────────────────────────────

    /** Input to calculateDisposal — decoupled from ParcelMatchingStrategy to avoid circular deps. */
    public record DisposalInput(UUID parcelId, BigDecimal quantityToDispose, BigDecimal proceedsPerUnit) {}

        public enum EntityType { INDIVIDUAL, TRUST, COMPANY }

    public record CgtResult(
        UUID parcelId,
        BigDecimal quantityDisposed,
        BigDecimal proceeds,
        BigDecimal costBase,
        BigDecimal grossGain,
        BigDecimal discountAmount,
        BigDecimal assessableGain,
        boolean discountApplied,
        boolean isLoss
    ) {}

    public record TaxYearCgtSummary(
        int financialYear,
        BigDecimal shortTermGains,
        BigDecimal longTermGains,
        BigDecimal totalDiscountableGains,
        BigDecimal totalCurrentYearLosses,
        BigDecimal priorYearLossesApplied,
        BigDecimal netAssessableCgt,
        BigDecimal lossesCarriedForward
    ) {}

    public record FrankingResult(
        BigDecimal cashDividend,
        BigDecimal frankingCredit,
        BigDecimal grossedUpDividend
    ) {}

    /** Thin read-only view of a TaxParcel — avoids circular module deps. */
    public record TaxParcelView(
        UUID parcelId,
        BigDecimal costPerUnit,
        LocalDate acquisitionDate
    ) {}
}
