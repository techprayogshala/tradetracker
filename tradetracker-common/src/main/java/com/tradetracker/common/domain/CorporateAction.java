package com.tradetracker.common.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sealed interface for corporate actions.
 * Exhaustive modelling: unhandled action types are compile errors.
 */
public sealed interface CorporateAction
    permits StockSplit, Merger, SpinOff, RightsIssue, ReturnOfCapital {

    String ticker();
    LocalDate effectiveDate();
}

record StockSplit(
    String ticker,
    LocalDate effectiveDate,
    BigDecimal splitRatio        // e.g. 2.0 for a 2-for-1 split
) implements CorporateAction {}

record Merger(
    String ticker,               // Acquired company
    String acquirerTicker,
    LocalDate effectiveDate,
    BigDecimal exchangeRatio
) implements CorporateAction {}

record SpinOff(
    String ticker,
    String spunOffTicker,
    LocalDate effectiveDate,
    BigDecimal costBasisAllocationRatio
) implements CorporateAction {}

record RightsIssue(
    String ticker,
    LocalDate effectiveDate,
    BigDecimal issuePrice,
    BigDecimal ratio
) implements CorporateAction {}

record ReturnOfCapital(
    String ticker,
    LocalDate effectiveDate,
    BigDecimal amountPerShare
) implements CorporateAction {}
