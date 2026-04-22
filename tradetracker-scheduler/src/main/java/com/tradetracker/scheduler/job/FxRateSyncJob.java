package com.tradetracker.scheduler.job;

import com.tradetracker.marketdata.service.PriceSyncService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("FxRateSyncJob")
@DisallowConcurrentExecution
public class FxRateSyncJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(FxRateSyncJob.class);

    @Autowired private PriceSyncService priceSyncService;
    @Autowired private JdbcClient jdbc;

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("FxRateSyncJob starting");
        try {
            List<String> currencies = jdbc.sql("""
                SELECT DISTINCT currency FROM securities WHERE currency != 'USD'
                UNION
                SELECT DISTINCT base_currency FROM portfolios WHERE base_currency != 'USD'
                """)
                .query(String.class)
                .list();

            if (currencies.isEmpty()) {
                log.info("FxRateSyncJob: no non-USD currencies, skipping");
                return;
            }
            priceSyncService.syncFxRates(currencies);
            log.info("FxRateSyncJob done — synced {} currencies", currencies.size());
        } catch (Exception e) {
            log.error("FxRateSyncJob failed", e);
            throw new JobExecutionException(e, false);
        }
    }
}