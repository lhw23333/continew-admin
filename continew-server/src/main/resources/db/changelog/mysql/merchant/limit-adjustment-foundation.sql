-- liquibase formatted sql

-- changeset continew:merchant-phase1-limit-adjustment-foundation-mysql
ALTER TABLE `biz_limit_adjustment`
    ADD COLUMN `active_request_guard` varchar(32) DEFAULT NULL COMMENT '活动申请唯一保护'
        AFTER `effective_status`,
    ADD COLUMN `amount_policy_version` varchar(64) DEFAULT NULL COMMENT '金额规则版本'
        AFTER `channel_config_version`;

CREATE UNIQUE INDEX `uk_limit_request_no`
    ON `biz_limit_adjustment` (`tenant_id`, `request_no`);

CREATE UNIQUE INDEX `uk_limit_active_dimension`
    ON `biz_limit_adjustment`
    (`tenant_id`, `merchant_id`, `channel_code`, `platform_code`, `active_request_guard`);

CREATE TABLE IF NOT EXISTS `biz_limit_adjustment_history` (
    `id`                     bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`              bigint(20)    NOT NULL COMMENT '租户ID',
    `request_id`             bigint(20)    NOT NULL COMMENT '限额申请ID',
    `request_version`        bigint(20)    NOT NULL COMMENT '申请业务版本',
    `action`                 varchar(64)   NOT NULL COMMENT '领域动作',
    `approval_status`        varchar(32)   NOT NULL COMMENT '审批状态',
    `channel_status`         varchar(32)   NOT NULL COMMENT '渠道状态',
    `effective_status`       varchar(32)   NOT NULL COMMENT '生效状态',
    `original_limit`         decimal(20,2) NOT NULL COMMENT '原生效限额快照',
    `requested_limit`        decimal(20,2) NOT NULL COMMENT '输入申请限额快照',
    `normalized_limit`       decimal(20,2) NOT NULL COMMENT '规则归一化限额快照',
    `effective_limit`        decimal(20,2) DEFAULT NULL COMMENT '最终生效限额快照',
    `amount_policy_version`  varchar(64)   DEFAULT NULL COMMENT '金额规则版本快照',
    `actor_user_id`          bigint(20)    NOT NULL COMMENT '操作人',
    `opinion`                varchar(2000) DEFAULT NULL COMMENT '已净化意见',
    `channel_result_code`    varchar(128)  DEFAULT NULL COMMENT '渠道结果码',
    `channel_result_message` varchar(1000) DEFAULT NULL COMMENT '已净化渠道结果说明',
    `occurred_time`          datetime(3)   NOT NULL COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_limit_history_request` (`tenant_id`, `request_id`, `request_version`, `occurred_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户限额调整不可变历史';

-- changeset continew:merchant-phase1-limit-adjustment-history-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_limit_adjustment_history_no_update`
    BEFORE UPDATE ON `biz_limit_adjustment_history`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_limit_adjustment_history is append-only';

-- changeset continew:merchant-phase1-limit-adjustment-history-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_limit_adjustment_history_no_delete`
    BEFORE DELETE ON `biz_limit_adjustment_history`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_limit_adjustment_history is append-only';

-- changeset continew:merchant-phase1-limit-adjustment-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_limit_adjustment_no_delete`
    BEFORE DELETE ON `biz_limit_adjustment`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_limit_adjustment cannot be deleted';
