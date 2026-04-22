package com.tradetracker.scheduler.job;

import com.tradetracker.notification.listener.NotificationEventListener;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Weekly job that scans for parcels approaching 12-month CGT discount eligibility
 * and sends reminder emails to portfolio owners.
 *
 * Triggered every Monday at 9am AEST (Sunday 11pm UTC).
 * Registered in SchedulerConfig alongside the other jobs.
 */
@Component
public class CgtDiscountReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CgtDiscountReminderJob.class);

    @Autowired
    private NotificationEventListener notificationListener;

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("CgtDiscountReminderJob starting");
        try {
            notificationListener.enqueueCgtDiscountReminders();
            log.info("CgtDiscountReminderJob complete");
        } catch (Exception e) {
            log.error("CgtDiscountReminderJob failed", e);
            throw new JobExecutionException(e, false);
        }
    }
}
