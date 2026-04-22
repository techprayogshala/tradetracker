package com.tradetracker.scheduler.job;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component("StaleHoldingsCacheEvictJob")
public class StaleHoldingsCacheEvictJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(StaleHoldingsCacheEvictJob.class);

    @Autowired private CacheManager cacheManager;

    @Override
    public void execute(JobExecutionContext ctx) {
        evict("holdings");
        evict("prices");
        evict("portfolios");
        log.debug("Cache eviction complete at {}", ctx.getFireTime());
    }

    private void evict(String name) {
        var cache = cacheManager.getCache(name);
        if (cache != null) cache.clear();
    }
}
