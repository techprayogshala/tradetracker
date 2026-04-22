-- V2__scheduler_broker_sync_job.sql
-- Quartz job definition for nightly broker sync.
-- The actual job bean is registered in SchedulerConfig; this migration is a
-- schema marker so Flyway tracks that broker sync was added in this version.
-- No DDL changes required — Quartz JDBC tables created in V1 handle all jobs.
SELECT 1;  -- No-op marker migration
