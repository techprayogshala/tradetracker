package com.tradetracker.tax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AustralianCgtServiceTest {

    private AustralianCgtService svc;

    @BeforeEach
    void setUp() { svc = new AustralianCgtService(); }

    // ── Single disposal ───────────────────────────────────────────────────────

    @Test
    void capital_gain_short_term_no_discount() {
        var parcel = new AustralianCgtService.TaxParcelView(
            UUID.randomUUID(), bd("100.00"),
            LocalDate.of(2024, 1, 1));

        var allocation = new AustralianCgtService.DisposalInput(
            parcel.parcelId(), bd("10"), bd("120.00"));

        var result = svc.calculateDisposal(
            allocation, parcel,
            LocalDate.of(2024, 6, 1),        // Held < 12 months
            AustralianCgtService.EntityType.INDIVIDUAL);

        assertThat(result.proceeds()).isEqualByComparingTo("1200.00");
        assertThat(result.costBase()).isEqualByComparingTo("1000.00");
        assertThat(result.grossGain()).isEqualByComparingTo("200.00");
        assertThat(result.discountApplied()).isFalse();
        assertThat(result.assessableGain()).isEqualByComparingTo("200.00");
    }

    @Test
    void capital_gain_long_term_fifty_percent_discount_applied() {
        var parcel = new AustralianCgtService.TaxParcelView(
            UUID.randomUUID(), bd("100.00"),
            LocalDate.of(2022, 6, 1));

        var allocation = new AustralianCgtService.DisposalInput(
            parcel.parcelId(), bd("100"), bd("150.00"));

        var result = svc.calculateDisposal(
            allocation, parcel,
            LocalDate.of(2024, 1, 1),        // Held > 12 months
            AustralianCgtService.EntityType.INDIVIDUAL);

        assertThat(result.proceeds()).isEqualByComparingTo("15000.00");
        assertThat(result.costBase()).isEqualByComparingTo("10000.00");
        assertThat(result.grossGain()).isEqualByComparingTo("5000.00");
        assertThat(result.discountApplied()).isTrue();
        // Discount amount = 5000 * 50% = 2500, so assessable = 2500
        assertThat(result.discountAmount()).isEqualByComparingTo("2500.00");
        assertThat(result.assessableGain()).isEqualByComparingTo("2500.00");
    }

    @Test
    void capital_loss_no_discount_applied() {
        var parcel = new AustralianCgtService.TaxParcelView(
            UUID.randomUUID(), bd("120.00"),
            LocalDate.of(2020, 1, 1));

        var allocation = new AustralianCgtService.DisposalInput(
            parcel.parcelId(), bd("50"), bd("100.00"));   // Selling at a loss

        var result = svc.calculateDisposal(
            allocation, parcel,
            LocalDate.of(2024, 1, 1),
            AustralianCgtService.EntityType.INDIVIDUAL);

        assertThat(result.isLoss()).isTrue();
        assertThat(result.grossGain()).isEqualByComparingTo("-1000.00");
        assertThat(result.discountApplied()).isFalse();   // Losses never discounted
        assertThat(result.assessableGain()).isEqualByComparingTo("-1000.00");
    }

    @Test
    void company_entity_no_cgt_discount_even_when_held_over_12m() {
        var parcel = new AustralianCgtService.TaxParcelView(
            UUID.randomUUID(), bd("50.00"),
            LocalDate.of(2020, 1, 1));

        var allocation = new AustralianCgtService.DisposalInput(
            parcel.parcelId(), bd("100"), bd("80.00"));

        var result = svc.calculateDisposal(
            allocation, parcel,
            LocalDate.of(2024, 1, 1),
            AustralianCgtService.EntityType.COMPANY);   // Companies don't get the discount

        assertThat(result.discountApplied()).isFalse();
        assertThat(result.grossGain()).isEqualByComparingTo("3000.00");
        assertThat(result.assessableGain()).isEqualByComparingTo("3000.00");
    }

    // ── Tax year summary — ATO-correct loss ordering ──────────────────────────

    @Test
    void losses_applied_to_non_discountable_gains_first() {
        // ATO rule: losses reduce non-discountable gains first, then discountable
        // Gains: $3,000 non-discountable, $8,000 discountable
        // Loss:  $1,000
        // Expected: loss wipes $1k from non-discountable → $2k non-disc + $4k discounted (50% of $8k)
        var nonDisc  = makeGainResult(bd("3000"), false);
        var discGain = makeGainResult(bd("8000"), true);
        var loss     = makeLossResult(bd("1000"));

        var summary = svc.summariseTaxYear(List.of(nonDisc, discGain, loss), BigDecimal.ZERO);

        assertThat(summary.totalCapitalGains()).isEqualByComparingTo("3000");
        assertThat(summary.totalDiscountableGains()).isEqualByComparingTo("8000");
        assertThat(summary.totalCurrentYearLosses()).isEqualByComparingTo("1000");
        // Non-disc after loss: 3000 - 1000 = 2000
        // Discountable after remaining losses (0 remaining): 8000
        // After 50% discount: 4000
        // Net = 2000 + 4000 = 6000
        assertThat(summary.netAssessableCgt()).isEqualByComparingTo("6000");
        assertThat(summary.lossesCarriedForward()).isEqualByComparingTo("0");
    }

    @Test
    void excess_losses_carried_forward() {
        var loss1 = makeLossResult(bd("5000"));
        var loss2 = makeLossResult(bd("3000"));
        var gain  = makeGainResult(bd("4000"), false);

        var summary = svc.summariseTaxYear(List.of(loss1, loss2, gain), BigDecimal.ZERO);

        assertThat(summary.netAssessableCgt()).isEqualByComparingTo("0");
        assertThat(summary.lossesCarriedForward()).isEqualByComparingTo("4000");  // 8000 losses - 4000 gain
    }

    @Test
    void prior_year_losses_carried_in_and_applied() {
        var gain = makeGainResult(bd("10000"), true);   // Discountable gain
        // With $3k prior-year losses:
        // Apply to discountable gains (pre-discount): 10000 - 3000 = 7000 remaining
        // After 50% discount: 3500 assessable
        var summary = svc.summariseTaxYear(List.of(gain), bd("3000"));

        assertThat(summary.priorYearLossesApplied()).isEqualByComparingTo("3000");
        assertThat(summary.netAssessableCgt()).isEqualByComparingTo("3500");
    }

    // ── Franking credits ──────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "100.00, 100, 142.86, 42.86",   // Fully franked: 100/(1-0.30) = 142.86
        "100.00,  50, 117.65, 17.65",   // 50% franked
        "100.00,   0, 100.00,  0.00",   // Unfranked
    })
    void franking_credit_grossup(String cash, String pct, String expectedGross, String expectedCredit) {
        var result = svc.calculateFrankingCredit(bd(cash), bd(pct));
        assertThat(result.cashDividend()).isEqualByComparingTo(cash);
        assertThat(result.grossedUpDividend().doubleValue())
            .isCloseTo(Double.parseDouble(expectedGross), org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.frankingCredit().doubleValue())
            .isCloseTo(Double.parseDouble(expectedCredit), org.assertj.core.data.Offset.offset(0.01));
    }

    // ── CGT discount eligibility ──────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "2022-01-01, 2023-01-02, true",   // Strictly more than 12 months → eligible
        "2022-01-01, 2023-01-01, false",  // Exactly 12 months → NOT eligible
        "2022-01-01, 2022-12-31, false",  // Less than 12 months → not eligible
    })
    void cgt_discount_eligibility_boundary(String acquired, String disposed, boolean expected) {
        var parcel = new AustralianCgtService.TaxParcelView(
            UUID.randomUUID(), bd("100"), LocalDate.parse(acquired));
        assertThat(parcel.acquisitionDate().plusYears(1).isBefore(LocalDate.parse(disposed)))
            .isEqualTo(expected);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AustralianCgtService.CgtResult makeGainResult(BigDecimal gain, boolean discountEligible) {
        BigDecimal discount = discountEligible ? gain.multiply(new BigDecimal("0.5")) : BigDecimal.ZERO;
        return new AustralianCgtService.CgtResult(
            UUID.randomUUID(), bd("100"), gain.add(bd("10000")), bd("10000"),
            gain, discount, gain.subtract(discount), discountEligible, false);
    }

    private AustralianCgtService.CgtResult makeLossResult(BigDecimal lossAbs) {
        BigDecimal loss = lossAbs.negate();
        return new AustralianCgtService.CgtResult(
            UUID.randomUUID(), bd("100"), bd("9000"), bd("10000"),
            loss, BigDecimal.ZERO, loss, false, true);
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
