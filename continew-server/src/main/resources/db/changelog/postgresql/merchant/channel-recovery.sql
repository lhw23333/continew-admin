-- liquibase formatted sql
-- changeset continew:merchant-phase1-channel-recovery-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_recovery" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL,
    "channel_code" varchar(64) NOT NULL, "product_code" varchar(64) NOT NULL,
    "config_version" varchar(64) NOT NULL, "command_operation" varchar(64) NOT NULL,
    "query_operation" varchar(64) DEFAULT NULL, "business_type" varchar(64) NOT NULL,
    "business_id" int8 NOT NULL, "business_version" int8 NOT NULL,
    "business_serial" varchar(128) NOT NULL, "trace_id" varchar(64) NOT NULL,
    "status" varchar(32) NOT NULL, "retry_count" int4 NOT NULL DEFAULT 0,
    "next_retry_time" timestamp DEFAULT NULL, "last_error_category" varchar(64) DEFAULT NULL,
    "locked_by" varchar(128) DEFAULT NULL, "locked_time" timestamp DEFAULT NULL,
    "resolved_event_id" int8 DEFAULT NULL, "resolved_time" timestamp DEFAULT NULL,
    "alert_status" varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    "create_time" timestamp NOT NULL, "update_time" timestamp DEFAULT NULL,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_channel_recovery_command" ON "biz_channel_recovery"
    ("tenant_id","channel_code","product_code","business_serial","command_operation");
CREATE INDEX "idx_channel_recovery_due" ON "biz_channel_recovery" ("status","next_retry_time","id");
CREATE INDEX "idx_channel_recovery_business" ON "biz_channel_recovery"
    ("tenant_id","business_type","business_id","create_time","id");
CREATE INDEX "idx_channel_recovery_alert" ON "biz_channel_recovery" ("alert_status","status","id");
COMMENT ON TABLE "biz_channel_recovery" IS '渠道不确定结果恢复任务';
