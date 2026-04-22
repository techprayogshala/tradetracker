package com.tradetracker.scheduler.job;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("PortfolioSnapshotJob")
@DisallowConcurrentExecution
public class PortfolioSnapshotJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotJob.class);

    @Autowired private JdbcClient jdbc;

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("PortfolioSnapshotJob starting for {}", LocalDate.now());
        try {
            int rows = jdbc.sql("""
                INSERT INTO portfolio_snapshots (portfolio_id, snapshot_date, market_value, cost_base)
                SELECT
                    tp.portfolio_id,
                    CURRENT_DATE                                              AS snapshot_date,
                    SUM(tp.quantity_remaining * p.adjusted_close)            AS market_value,
                    SUM(tp.quantity_remaining * tp.cost_per_unit)            AS cost_base
                FROM tax_parcels tp
                JOIN LATERAL (
                    SELECT adjusted_close
                    FROM security_prices sp
                    WHERE sp.security_id = tp.security_id
                    ORDER BY sp.price_date DESC
                    LIMIT 1
                ) p ON true
                WHERE tp.is_fully_disposed = false
                GROUP BY tp.portfolio_id
                ON CONFLICT (portfolio_id, snapshot_date)
                DO UPDATE SET
                    market_value = EXCLUDED.market_value,
                    cost_base    = EXCLUDED.cost_base,
                    updated_at   = NOW()
                """)
                .update();

            log.info("PortfolioSnapshotJob done — {} portfolios snapshotted", rows);
        } catch (Exception e) {
            log.error("PortfolioSnapshotJob failed", e);
            throw new JobExecutionException(e, false);
        }
    }
}
