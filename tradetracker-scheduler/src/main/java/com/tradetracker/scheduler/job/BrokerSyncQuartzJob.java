package com.tradetracker.scheduler.job;

import com.tradetracker.broker.service.BrokerSyncService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class BrokerSyncQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(BrokerSyncQuartzJob.class);

    @Autowired private BrokerSyncService brokerSyncService;

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("BrokerSyncJob starting");
        try {
            BrokerSyncService.SyncSummary summary = brokerSyncService.syncAll();
            log.info("BrokerSyncJob done: {} connections, {} imported, {} skipped, {} failed",
                summary.connections(), summary.imported(), summary.skipped(), summary.failed());
        } catch (Exception e) {
            log.error("BrokerSyncJob failed", e);
            throw new JobExecutionException(e, false);
        }
    }
}
