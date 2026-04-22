package com.tradetracker.scheduler.config;

import com.tradetracker.scheduler.job.CgtDiscountReminderJob;
import com.tradetracker.scheduler.job.FxRateSyncJob;
import com.tradetracker.scheduler.job.NightlyPriceSyncJob;
import com.tradetracker.scheduler.job.PortfolioSnapshotJob;
import com.tradetracker.scheduler.job.StaleHoldingsCacheEvictJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines all Quartz jobs and their CRON triggers.
 *
 * All jobs use {@code JobBuilder.storeDurably(true)} so they survive restarts.
 * Triggers use misfire instructions appropriate for each job's semantics.
 *
 * Schedule overview (all times AEST = UTC+10):
 *
 *   06:00 daily  — Nightly price sync   (after ASX closes at ~16:30 AEST)
 *   06:30 daily  — FX rate sync         (after overnight London/NY close)
 *   07:00 daily  — Portfolio snapshot   (captures EOD value for TWR calculation)
 *   Every 10 min — Cache eviction        (keeps holdings display fresh during trading hours)
 */
@Configuration
public class SchedulerConfig {

    // ── Nightly price sync ────────────────────────────────────────────────────

    @Bean
    public JobDetail nightlyPriceSyncJobDetail() {
        return JobBuilder.newJob(NightlyPriceSyncJob.class)
            .withIdentity("nightlyPriceSync")
            .withDescription("Sync last 100 days of prices for all watched securities")
            .storeDurably(true)
            .build();
    }

    @Bean
    public Trigger nightlyPriceSyncTrigger(JobDetail nightlyPriceSyncJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(nightlyPriceSyncJobDetail)
            .withIdentity("nightlyPriceSyncTrigger")
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 20 * * ?")       // 8pm UTC = 6am AEST
                .withMisfireHandlingInstructionDoNothing())
            .build();
    }

    // ── FX rate sync ─────────────────────────────────────────────────────────

    @Bean
    public JobDetail fxRateSyncJobDetail() {
        return JobBuilder.newJob(FxRateSyncJob.class)
            .withIdentity("fxRateSync")
            .withDescription("Sync daily FX rates for all portfolio currencies")
            .storeDurably(true)
            .build();
    }

    @Bean
    public Trigger fxRateSyncTrigger(JobDetail fxRateSyncJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(fxRateSyncJobDetail)
            .withIdentity("fxRateSyncTrigger")
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0 30 20 * * ?")      // 8:30pm UTC = 6:30am AEST
                .withMisfireHandlingInstructionDoNothing())
            .build();
    }

    // ── Portfolio daily snapshot ──────────────────────────────────────────────

    @Bean
    public JobDetail portfolioSnapshotJobDetail() {
        return JobBuilder.newJob(PortfolioSnapshotJob.class)
            .withIdentity("portfolioSnapshot")
            .withDescription("Record EOD portfolio value for TWR time-series")
            .storeDurably(true)
            .build();
    }

    @Bean
    public Trigger portfolioSnapshotTrigger(JobDetail portfolioSnapshotJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(portfolioSnapshotJobDetail)
            .withIdentity("portfolioSnapshotTrigger")
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 21 * * ?")       // 9pm UTC = 7am AEST
                .withMisfireHandlingInstructionDoNothing())
            .build();
    }

    // ── CGT discount reminders ────────────────────────────────────────────────

    @Bean
    public JobDetail cgtReminderJobDetail() {
        return JobBuilder.newJob(CgtDiscountReminderJob.class)
            .withIdentity("cgtDiscountReminder")
            .withDescription("Weekly CGT discount eligibility reminder emails")
            .storeDurably(true)
            .build();
    }

    @Bean
    public Trigger cgtReminderTrigger(JobDetail cgtReminderJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(cgtReminderJobDetail)
            .withIdentity("cgtReminderTrigger")
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 23 ? * SUN")     // Sunday 11pm UTC = Monday 9am AEST
                .withMisfireHandlingInstructionDoNothing())
            .build();
    }

    // ── Broker sync ───────────────────────────────────────────────────────────

    @Bean
    public JobDetail brokerSyncJobDetail() {
        return JobBuilder.newJob(com.tradetracker.scheduler.job.BrokerSyncQuartzJob.class)
            .withIdentity("brokerSync")
            .withDescription("Sync trades from all connected broker accounts")
            .storeDurably(true)
            .build();
    }

    @Bean
    public Trigger brokerSyncTrigger(JobDetail brokerSyncJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(brokerSyncJobDetail)
            .withIdentity("brokerSyncTrigger")
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 19 * * ?")       // 7pm UTC = 5am AEST — before markets open
                .withMisfireHandlingInstructionDoNothing())
            .build();
    }

    // ── Cache eviction ────────────────────────────────────────────────────────

    @Bean
    public JobDetail cacheEvictJobDetail() {
        return JobBuilder.newJob(StaleHoldingsCacheEvictJob.class)
            .withIdentity("cacheEvict")
            .withDescription("Evict stale holdings cache so UI shows near-live prices")
            .storeDurably(true)
            .build();
    }

    @Bean
    public Trigger cacheEvictTrigger(JobDetail cacheEvictJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(cacheEvictJobDetail)
            .withIdentity("cacheEvictTrigger")
            .withSchedule(SimpleScheduleBuilder
                .simpleSchedule()
                .withIntervalInMinutes(10)
                .repeatForever()
                .withMisfireHandlingInstructionNextWithRemainingCount())
            .build();
    }
}
