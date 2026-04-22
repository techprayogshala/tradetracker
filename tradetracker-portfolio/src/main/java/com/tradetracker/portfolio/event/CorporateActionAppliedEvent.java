package com.tradetracker.portfolio.event;

import java.util.UUID;

/**
 * Published when a corporate action is applied retroactively.
 * Triggers a full recalculation of all affected portfolios.
 */
public record CorporateActionAppliedEvent(
        UUID corporateActionId,
        String ticker,
        String actionType
) {
}
