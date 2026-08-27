-- liquibase formatted sql
-- changeset continew:merchant-phase1-channel-recovery-mysql
CREATE TABLE IF NOT EXISTS `biz_channel_recovery` (
    `id` bigint(20) NOT NULL, `tenant_id` bigint(20) NOT NULL,
    `channel_code` varchar(64) NOT NULL, `product_code` varchar(64) NOT NULL,
    `config_version` varchar(64) NOT NULL, `command_operation` varchar(64) NOT NULL,
    `query_operation` varchar(64) DEFAULT NULL, `business_type` varchar(64) NOT NULL,
    `business_id` bigint(20) NOT NULL, `business_version` bigint(20) NOT NULL,
    `business_serial` varchar(128) NOT NULL, `trace_id` varchar(64) NOT NULL,
    `status` varchar(32) NOT NULL, `retry_count` int NOT NULL DEFAULT 0,
    `next_retry_time` datetime(3) DEFAULT NULL, `last_error_category` varchar(64) DEFAULT NULL,
    `locked_by` varchar(128) DEFAULT NULL, `locked_time` datetime(3) DEFAULT NULL,
    `resolved_event_id` bigint(20) DEFAULT NULL, `resolved_time` datetime(3) DEFAULT NULL,
    `alert_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    `create_time` datetime(3) NOT NULL, `update_time` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_recovery_command`
      (`tenant_id`,`channel_code`,`product_code`,`business_serial`,`command_operation`),
    KEY `idx_channel_recovery_due` (`status`,`next_retry_time`,`id`),
    KEY `idx_channel_recovery_business` (`tenant_id`,`business_type`,`business_id`,`create_time`,`id`),
    KEY `idx_channel_recovery_alert` (`alert_status`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道不确定结果恢复任务';
