-- V19: Add missing Quartz tables for Quartz 2.3.2 compatibility
-- These tables were missing from V17 migration

CREATE TABLE IF NOT EXISTS qrtz_paused_trigger_grps (
    sched_name    VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_calendars (
    sched_name    VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar     BYTEA        NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE IF NOT EXISTS qrtz_blob_triggers (
    sched_name    VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data    BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_simprop_triggers (
    sched_name        VARCHAR(120) NOT NULL,
    trigger_name     VARCHAR(200) NOT NULL,
    trigger_group    VARCHAR(200) NOT NULL,
    str_prop_1       VARCHAR(512),
    str_prop_2       VARCHAR(512),
    str_prop_3       VARCHAR(512),
    int_prop_1       INTEGER,
    int_prop_2       INTEGER,
    long_prop_1      BIGINT,
    long_prop_2      BIGINT,
    dec_prop_1       NUMERIC(13,4),
    dec_prop_2       NUMERIC(13,4),
    bool_prop_1      BOOLEAN,
    bool_prop_2      BOOLEAN,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE INDEX IF NOT EXISTS idx_qrtz_t_j        ON qrtz_triggers (sched_name, job_name, job_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_t_jg       ON qrtz_triggers (sched_name, job_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_t_c        ON qrtz_triggers (sched_name, calendar_name);
CREATE INDEX IF NOT EXISTS idx_qrtz_t_g        ON qrtz_triggers (sched_name, trigger_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_ft_inst_job ON qrtz_fired_triggers (sched_name, instance_name, job_name, job_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_ft_j_g      ON qrtz_fired_triggers (sched_name, job_name, job_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_ft_jg      ON qrtz_fired_triggers (sched_name, job_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_ft_t_g     ON qrtz_fired_triggers (sched_name, trigger_name, trigger_group);
CREATE INDEX IF NOT EXISTS idx_qrtz_ft_tg     ON qrtz_fired_triggers (sched_name, trigger_group);
