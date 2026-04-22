package com.tradetracker.scheduler.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Extends SpringBeanJobFactory so Quartz job instances are created through
 * Spring's BeanFactory, enabling full @Autowired dependency injection.
 *
 * Without this, every @Autowired field in a Job class is null at runtime —
 * Quartz instantiates jobs via newInstance() without involving Spring at all.
 *
 * Registered as a @Bean in QuartzConfig which sets it on the SchedulerFactory.
 */
public class AutowiringSpringBeanJobFactory
        extends SpringBeanJobFactory
        implements ApplicationContextAware {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.beanFactory = ctx.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);       // Inject @Autowired fields
        beanFactory.initializeBean(job, job.getClass().getName());
        return job;
    }
}
