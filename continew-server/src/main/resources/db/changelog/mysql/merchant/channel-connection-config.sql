-- liquibase formatted sql
-- changeset continew:merchant-phase1-channel-connection-config-mysql
CREATE TABLE IF NOT EXISTS `biz_channel_connection_version` (
 `id` bigint(20) NOT NULL, `tenant_id` bigint(20) NOT NULL,
 `channel_code` varchar(64) NOT NULL, `product_code` varchar(64) NOT NULL, `config_version` varchar(64) NOT NULL,
 `endpoint_json` text NOT NULL, `timeout_json` text NOT NULL,
 `status_mapping_version` varchar(64) NOT NULL, `status_mapping_json` text NOT NULL,
 `signing_key_ref` varchar(255) NOT NULL, `encryption_key_ref` varchar(255) DEFAULT NULL,
 `callback_verification_key_ref` varchar(255) NOT NULL,
 `status` varchar(32) NOT NULL DEFAULT 'DISABLED', `effective_time` datetime(3) NOT NULL,
 `expires_time` datetime(3) DEFAULT NULL, `create_user` bigint(20) DEFAULT NULL,
 `create_time` datetime(3) NOT NULL, `update_user` bigint(20) DEFAULT NULL,
 `update_time` datetime(3) DEFAULT NULL, `deleted` bigint(20) NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), UNIQUE INDEX `uk_channel_connection_version` (`tenant_id`,`channel_code`,`product_code`,`config_version`,`deleted`),
 INDEX `idx_channel_connection_effective` (`tenant_id`,`channel_code`,`product_code`,`status`,`effective_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道连接与安全引用版本表';
-- changeset continew:merchant-phase1-channel-connection-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_channel_connection_no_update` BEFORE UPDATE ON `biz_channel_connection_version` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_connection_version is append-only';
-- changeset continew:merchant-phase1-channel-connection-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_channel_connection_no_delete` BEFORE DELETE ON `biz_channel_connection_version` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_connection_version is append-only';
