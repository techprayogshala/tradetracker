package com.tradetracker.scheduler.job;

import com.tradetracker.marketdata.service.PriceSyncService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("NightlyPriceSyncJob")
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class NightlyPriceSyncJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(NightlyPriceSyncJob.class);

    @Autowired private PriceSyncService priceSyncService;

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("NightlyPriceSyncJob starting");
        try {
            PriceSyncService.SyncResult result = priceSyncService.syncRecentPrices();
            log.info("NightlyPriceSyncJob done — {} securities, {} rows upserted, {} failed",
                result.securities(), result.pricesUpserted(), result.failed());
            ctx.getJobDetail().getJobDataMap().put("lastResult",
                "securities=%d pricesUpserted=%d failed=%d"
                    .formatted(result.securities(), result.pricesUpserted(), result.failed()));
        } catch (Exception e) {
            log.error("NightlyPriceSyncJob failed", e);
            throw new JobExecutionException(e, false);
        }
    }
}