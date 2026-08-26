-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-event-application-state-mysql
ALTER TABLE `biz_onboarding_application`
    ADD COLUMN `channel_business_serial` varchar(128) DEFAULT NULL COMMENT '绑定渠道业务流水号' AFTER `idempotency_key`,
    ADD COLUMN `reporting_rank` int NOT NULL DEFAULT 0 COMMENT '报件映射进度' AFTER `reporting_status`,
    ADD COLUMN `agreement_rank` int NOT NULL DEFAULT 0 COMMENT '签约映射进度' AFTER `agreement_status`,
    ADD COLUMN `card_binding_rank` int NOT NULL DEFAULT 0 COMMENT '绑卡映射进度' AFTER `card_binding_status`,
    ADD COLUMN `reserve_account_rank` int NOT NULL DEFAULT 0 COMMENT '备付金映射进度' AFTER `reserve_account_status`,
    ADD COLUMN `channel_final_rank` int NOT NULL DEFAULT 0 COMMENT '最终状态映射进度' AFTER `channel_final_status`,
    ADD COLUMN `channel_final_terminal` tinyint(1) NOT NULL DEFAULT 0 COMMENT '渠道最终状态是否终结' AFTER `channel_final_rank`;

CREATE INDEX `idx_onboarding_channel_serial`
    ON `biz_onboarding_application` (`tenant_id`, `channel_code`, `channel_business_serial`, `id`);

-- changeset continew:merchant-phase1-channel-event-normalized-snapshot-mysql
ALTER TABLE `biz_channel_event`
    ADD COLUMN `product_code` varchar(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '渠道产品编码' AFTER `channel_code`,
    ADD COLUMN `config_version` varchar(64) NOT NULL DEFAULT 'LEGACY' COMMENT '连接配置版本' AFTER `product_code`,
    ADD COLUMN `business_type` varchar(64) NOT NULL DEFAULT 'ONBOARDING' COMMENT '业务类型' AFTER `merchant_id`,
    ADD COLUMN `business_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '业务版本' AFTER `business_type`,
    ADD COLUMN `channel_request_id` varchar(191) DEFAULT NULL COMMENT '渠道请求ID' AFTER `event_type`,
    ADD COLUMN `operation_status` varchar(32) DEFAULT NULL COMMENT '归一化操作状态' AFTER `normalized_status`,
    ADD COLUMN `reporting_status` varchar(32) DEFAULT NULL COMMENT '报件快照' AFTER `operation_status`,
    ADD COLUMN `signing_status` varchar(32) DEFAULT NULL COMMENT '签约快照' AFTER `reporting_status`,
    ADD COLUMN `card_binding_status` varchar(32) DEFAULT NULL COMMENT '绑卡快照' AFTER `signing_status`,
    ADD COLUMN `reserve_account_status` varchar(32) DEFAULT NULL COMMENT '备付金快照' AFTER `card_binding_status`,
    ADD COLUMN `final_status` varchar(32) DEFAULT NULL COMMENT '最终状态快照' AFTER `reserve_account_status`,
    ADD COLUMN `progression_rank` int DEFAULT NULL COMMENT '映射进度' AFTER `final_status`,
    ADD COLUMN `state_applied` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否改变领域状态' AFTER `processing_status`;

CREATE INDEX `idx_channel_event_business_serial`
    ON `biz_channel_event` (`tenant_id`, `channel_code`, `product_code`, `business_serial`, `received_time`, `id`);
